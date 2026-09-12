# -*- coding: utf-8 -*-
"""BBS AI Studio Blender 导出插件（.bbsm 单文件）

把 Blender 中的人物模型（网格 + 骨骼架 + 权重 + UV + 姿态 + 纹理）导出为
BBS AI Studio 统一模型格式 ``.bbsm``（GZIP + JSON 单文件）。

导出的文件放入 ``config/bbs/imports/models/`` 后，可在游戏内
「BBS AI Studio 面板 → 模型 → 模型浏览器」中导入（Ctrl+Shift+O）。

安装：Blender → Edit → Preferences → Add-ons → Install → 选择本文件 → 勾选
「BBS AI Studio Exporter」。导出：File → Export → BBS AI Studio Model (.bbsm)。

坐标约定：Blender（Z 朝上、-Y 前方）→ BBS（Y 朝上、-Z 前方），
变换为 ``bbs = (x, -z, y)``；骨骼绑定矩阵做对应的基变换
``M_bbs = A · M_blender · A⁻¹``。

作者：BBS AI Studio
"""

import base64
import gzip
import json
import math
import os
import time

import bpy
from bpy.props import BoolProperty, StringProperty
from bpy_extras.io_utils import ExportHelper
from bpy.types import Operator, Panel

FORMAT = "bbs_ai_studio_model_v1"
FORMAT_VERSION = "1.0"

# Blender → BBS 坐标变换：bbs = (x, -z, y)
AXIS = ((1.0, 0.0, 0.0), (0.0, 0.0, 1.0), (0.0, -1.0, 0.0))


def to_bbs_vec(v):
    """Blender 向量 → BBS 坐标向量"""
    return (v.x, -v.z, v.y)


def matrix_to_bbs_rows(m):
    """Blender 4x4 矩阵（mathutils 或嵌套列表）→ BBS 基变换后的行主序嵌套列表

    变换为 ``M_bbs = A · M_blender · A⁻¹``（A 为 AXIS 旋转，A⁻¹ = Aᵀ），
    平移分量再做 ``A · t`` 基变换。
    """
    rows = [[float(m[i][j]) for j in range(4)] for i in range(4)]
    a = AXIS
    out = [[0.0] * 4 for _ in range(4)]

    for i in range(3):
        for j in range(3):
            out[i][j] = sum(a[i][k] * rows[k][l] * a[j][l] for k in range(3) for l in range(3))
        out[i][3] = a[i][0] * rows[0][3] + a[i][1] * rows[1][3] + a[i][2] * rows[2][3]

    out[3] = [0.0, 0.0, 0.0, 1.0]
    return out


def flat_column_major(rows):
    """行主序嵌套列表 → 列主序扁平列表（.bbsm 矩阵约定）"""
    return [rows[j][i] for i in range(4) for j in range(4)]


def invert_rows(rows):
    """4x4 矩阵求逆（高斯消元；行主序嵌套列表）"""
    n = 4
    a = [row[:] + [1.0 if i == j else 0.0 for j in range(n)] for i, row in enumerate(rows)]

    for col in range(n):
        pivot = max(range(col, n), key=lambda r: abs(a[r][col]))
        if abs(a[pivot][col]) < 1e-12:
            return [[1.0 if i == j else 0.0 for j in range(n)] for i in range(n)]
        a[col], a[pivot] = a[pivot], a[col]
        p = a[col][col]
        a[col] = [v / p for v in a[col]]
        for r in range(n):
            if r != col and a[r][col] != 0.0:
                f = a[r][col]
                a[r] = [v - f * w for v, w in zip(a[r], a[col])]

    return [row[n:] for row in a]


def find_armature(context, selected_only):
    objects = context.selected_objects if selected_only else context.scene.objects
    for obj in objects:
        if obj.type == "ARMATURE":
            return obj
    for obj in context.scene.objects:
        if obj.type == "ARMATURE":
            return obj
    return None


def export_skeleton(armature):
    """导出骨骼层级（.bbsm 的 skeleton 段）"""
    if armature is None:
        return None

    bones = []
    for index, bone in enumerate(armature.data.bones):
        bind = matrix_to_bbs_rows(bone.matrix_local)

        bones.append({
            "name": bone.name,
            "parent": bone.parent.name if bone.parent else "",
            "index": index,
            "head": list(to_bbs_vec(bone.head_local)),
            "tail": list(to_bbs_vec(bone.tail_local)),
            "bind_matrix": flat_column_major(bind),
            "inverse_bind_matrix": flat_column_major(invert_rows(bind)),
            "deform": bone.use_deform,
        })

    return {"name": armature.name, "bones": bones}


def export_geometry(context, armature, selected_only):
    """导出网格几何：全局顶点/UV/法线 + 每网格三角面（索引三联 p/t/n，-1 缺省）"""
    objects = context.selected_objects if selected_only else context.scene.objects

    vertices = []
    vertex_weights = []
    uvs = []
    normals = []
    meshes = []

    for obj in objects:
        if obj.type != "MESH":
            continue

        mesh = obj.to_mesh()
        mesh.calc_normals_split()

        armature_matrix = obj.matrix_world
        if armature is not None and obj.parent == armature:
            armature_matrix = obj.matrix_world

        base_vertex = len(vertices)
        base_uv = len(uvs)
        base_normal = len(normals)

        group_names = [group.name for group in obj.vertex_groups]

        for vert in mesh.vertices:
            world = armature_matrix @ vert.co
            vertices.append(list(to_bbs_vec(world)))

            weights = []
            for group in vert.groups:
                if 0 <= group.group < len(group_names) and group.weight > 0.01:
                    weights.append([group_names[group.group], round(group.weight, 6)])
            vertex_weights.append(weights)

        uv_layer = mesh.uv_layers.active.data if mesh.uv_layers else None

        triangles = []
        for poly in mesh.polygons:
            corners = list(poly.loop_indices)
            if len(corners) < 3:
                continue
            for i in range(1, len(corners) - 1):
                for loop_idx in (corners[0], corners[i], corners[i + 1]):
                    loop = mesh.loops[loop_idx]

                    p = base_vertex + loop.vertex_index

                    t = -1
                    if uv_layer is not None:
                        uv = uv_layer[loop_idx].uv
                        uvs.append([round(uv.x, 6), round(uv.y, 6)])
                        t = len(uvs) - 1

                    world_normal = armature_matrix.to_3x3() @ loop.normal
                    normals.append([round(c, 6) for c in to_bbs_vec(world_normal)])
                    n = len(normals) - 1

                    triangles.extend([p, t, n])

        material_name = ""
        if obj.material_slots and obj.material_slots[0].material is not None:
            material_name = obj.material_slots[0].material.name

        meshes.append({
            "name": obj.name,
            "armature": armature.name if armature else "",
            "material": material_name if material_name else obj.name,
            "triangles": triangles,
        })

        obj.to_mesh_clear()

    return {
        "vertices": vertices,
        "vertex_weights": vertex_weights,
        "uvs": uvs,
        "normals": normals,
        "meshes": meshes,
    }


def export_pose(armature):
    """导出当前姿态（每根骨骼的平移/欧拉角/缩放，角度制）"""
    transforms = {}
    if armature is None:
        return {"pose": transforms}

    for bone in armature.pose.bones:
        euler = bone.rotation_euler
        transforms[bone.name] = {
            "translate": list(to_bbs_vec(bone.location)),
            "rotate": [round(math.degrees(euler.x), 4), round(math.degrees(euler.y), 4), round(math.degrees(euler.z), 4)],
            "scale": [bone.scale.x, bone.scale.y, bone.scale.z],
        }

    return {"pose": transforms}


def export_ik_constraints(armature):
    """导出 IK 约束（Blender IK 约束 → .bbsm ik 段的链式描述）"""
    constraints = []
    if armature is None:
        return constraints

    for bone in armature.pose.bones:
        for constraint in bone.constraints:
            if constraint.type != "IK":
                continue

            constraints.append({
                "name": constraint.name,
                "target_bone": bone.name,
                "chain_length": constraint.chain_count,
                "pole_angle": round(math.degrees(constraint.pole_angle), 4),
                "influence": constraint.influence,
                "use_anchor": constraint.use_tail,
            })

    return constraints


def collect_textures(context, selected_only):
    """收集材质纹理（内嵌 Base64；role=material:<材质名>）"""
    textures = []
    objects = context.selected_objects if selected_only else context.scene.objects
    seen = set()

    for obj in objects:
        if obj.type != "MESH":
            continue

        for slot in obj.material_slots:
            material = slot.material
            if material is None or material.name in seen:
                continue

            seen.add(material.name)

            image = None
            if material.use_nodes:
                for node in material.node_tree.nodes:
                    if node.type == "TEX_IMAGE" and node.image is not None:
                        image = node.image
                        break
            elif material.active_texture is not None:
                image = material.active_texture.image

            if image is None or not image.has_data:
                continue

            try:
                pixels = image.pixels[:]
                width = image.size[0]
                height = image.size[1]
                channels = image.channels

                png = encode_png(pixels, width, height, channels)
                if png is None:
                    continue

                textures.append({
                    "role": "material:" + material.name,
                    "path": "textures/%s.png" % material.name.replace(" ", "_"),
                    "encoding": "base64",
                    "content": base64.b64encode(png).decode("ascii"),
                    "size": len(png),
                })
            except Exception as exc:  # noqa: BLE001
                print("[BBSM] 跳过纹理 %s: %s" % (material.name, exc))

    return textures


def encode_png(pixels, width, height, channels):
    """把像素数组编码为 PNG（无外部依赖的最小实现）"""
    try:
        import zlib

        color_type = {1: 0, 2: 2, 3: 2, 4: 6}.get(channels, 6)
        scanlines = bytearray()

        for y in range(height - 1, -1, -1):
            scanlines.append(0)
            row = pixels[y * width * channels:(y + 1) * width * channels]
            if channels == 3:
                scanlines.extend(int(round(c * 255)) for c in row)
            elif channels == 4:
                scanlines.extend(int(round(c * 255)) for c in row)
            elif channels == 1:
                scanlines.extend(int(round(c * 255)) for c in row)
            else:
                return None

        def chunk(tag, data):
            out = len(data).to_bytes(4, "big") + tag + data
            return out + zlib.crc32(tag + data).to_bytes(4, "big")

        header = width.to_bytes(4, "big") + height.to_bytes(4, "big") + bytes([8, color_type, 0, 0, 0])

        return (b"\x89PNG\r\n\x1a\n"
                + chunk(b"IHDR", header)
                + chunk(b"IDAT", zlib.compress(bytes(scanlines)))
                + chunk(b"IEND", b""))
    except Exception:
        return None


def write_bobj(geometry, skeleton):
    """geometry + skeleton 段 → .bobj 文本（游戏原生加载器指令）"""
    lines = []

    for vertex, weights in zip(geometry["vertices"], geometry["vertex_weights"]):
        lines.append("v %s %s %s" % tuple(fnum(c) for c in vertex))
        for weight in weights:
            lines.append("vw %s %s" % (weight[0], fnum(weight[1])))

    for uv in geometry["uvs"]:
        lines.append("vt %s %s" % (fnum(uv[0]), fnum(uv[1])))

    for normal in geometry["normals"]:
        lines.append("vn %s %s %s" % tuple(fnum(c) for c in normal))

    for mesh in geometry["meshes"]:
        lines.append("o %s" % mesh["name"])
        if mesh["armature"]:
            lines.append("o_arm %s" % mesh["armature"])

        triangles = mesh["triangles"]
        for i in range(0, len(triangles), 9):
            corners = []
            for corner in range(3):
                p = triangles[i + corner * 3] + 1
                t = triangles[i + corner * 3 + 1]
                n = triangles[i + corner * 3 + 2]
                if t < 0 and n < 0:
                    corners.append(str(p))
                elif n < 0:
                    corners.append("%d/%d" % (p, t + 1))
                elif t < 0:
                    corners.append("%d//%d" % (p, n + 1))
                else:
                    corners.append("%d/%d/%d" % (p, t + 1, n + 1))
            lines.append("f " + " ".join(corners))

    if skeleton is not None and skeleton["bones"]:
        lines.append("arm_name %s" % skeleton["name"])
        for bone in skeleton["bones"]:
            bind = bone["bind_matrix"]
            rows = [[bind[row * 4 + col] for col in range(4)] for row in range(4)]
            values = [fnum(rows[row][col]) for row in range(4) for col in range(4)]
            lines.append("arm_bone %s %s %s %s %s %s" % (
                bone["name"], bone["parent"],
                fnum(bone["head"][0]), fnum(bone["head"][1]), fnum(bone["head"][2]),
                " ".join(values),
            ))

    return "\n".join(lines) + "\n"


def fnum(value):
    """浮点数格式化（简洁且 .bobj 可解析）"""
    text = ("%.6f" % value).rstrip("0").rstrip(".")
    return text if text not in ("", "-") else "0"


class ExportBBSM(Operator, ExportHelper):
    """导出为 BBS AI Studio 模型格式（.bbsm 单文件）"""

    bl_idname = "export_scene.bbsm"
    bl_label = "Export BBSM"
    bl_options = {"PRESET"}

    filename_ext = ".bbsm"
    filter_glob: StringProperty(default="*.bbsm", options={"HIDDEN"})

    use_selection: BoolProperty(name="仅导出选中物体", default=False)
    export_animation: BoolProperty(name="导出姿态/IK", default=True)
    embed_textures: BoolProperty(name="内嵌材质纹理", default=True)

    def execute(self, context):
        armature = find_armature(context, self.use_selection)

        if armature is None:
            self.report({"WARNING"}, "场景中没有骨骼架（Armature），仅导出静态网格")

        geometry = export_geometry(context, armature, self.use_selection)
        skeleton = export_skeleton(armature)

        if not geometry["meshes"]:
            self.report({"ERROR"}, "没有可导出的网格物体")
            return {"CANCELLED"}

        name = os.path.splitext(os.path.basename(self.filepath))[0]

        model = {
            "format": FORMAT,
            "metadata": {
                "name": name,
                "author": os.environ.get("USERNAME", os.environ.get("USER", "")),
                "description": "Exported from Blender via BBS AI Studio exporter",
                "version": FORMAT_VERSION,
                "created_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
                "source": "blender",
                "tags": ["blender"],
            },
            "model_id": name,
            "model_files": {
                "model.bobj": {"encoding": "text", "content": write_bobj(geometry, skeleton)},
            },
            "textures": collect_textures(context, self.use_selection) if self.embed_textures else [],
            "geometry": geometry,
            "skeleton": skeleton,
        }

        if self.export_animation:
            model["pose"] = export_pose(armature)
            model["ik"] = {"chains": export_ik_constraints(armature)} if armature else None

        try:
            os.makedirs(os.path.dirname(self.filepath) or ".", exist_ok=True)
            with gzip.open(self.filepath, "wt", encoding="utf-8") as handle:
                json.dump(model, handle, ensure_ascii=False, separators=(",", ":"))
        except OSError as exc:
            self.report({"ERROR"}, "写入失败: %s" % exc)
            return {"CANCELLED"}

        size_kb = os.path.getsize(self.filepath) // 1024
        self.report(
            {"INFO"},
            "已导出 %s（%d 网格 / %d 顶点 / %d 骨骼 / %d KB）。放入 config/bbs/imports/models/ 即可在游戏内导入"
            % (self.filepath, len(geometry["meshes"]), len(geometry["vertices"]),
               len(skeleton["bones"]) if skeleton else 0, size_kb),
        )

        return {"FINISHED"}


class BBSMExportPanel(Panel):
    """BBS AI Studio 导出面板（3D 视图侧栏）"""

    bl_label = "BBS AI Studio"
    bl_idname = "VIEW3D_PT_bbsm_export"
    bl_space_type = "VIEW_3D"
    bl_region_type = "UI"
    bl_category = "BBS AI Studio"

    def draw(self, context):
        layout = self.layout
        layout.operator("export_scene.bbsm", text="导出 .bbsm", icon="EXPORT")
        layout.label(text="输出为单文件（GZIP+JSON）")
        layout.label(text="游戏内 Ctrl+Shift+O 导入")


classes = (ExportBBSM, BBSMExportPanel)


def register():
    for cls in classes:
        bpy.utils.register_class(cls)


def unregister():
    for cls in reversed(classes):
        bpy.utils.unregister_class(cls)


if __name__ == "__main__":
    register()
