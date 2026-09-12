package mchorse.bbs_ai.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.bobj.BOBJLoader;
import org.joml.Matrix4f;
import org.joml.Vector2d;
import org.joml.Vector3f;

/**
 * BOBJ 几何数据的双向转换器
 *
 * <p>导出方向：{@link BOBJLoader.BOBJData} → .bbsm 的 {@code geometry} / {@code skeleton} JSON 段
 * （供外部工具直读，不需要 BBS 运行时）。</p>
 *
 * <p>导入方向：{@code geometry} + {@code skeleton} JSON → .bobj 文本
 * （外部工具生成的 .bbsm 没有 model.bobj 时，导入器现场合成一个交给原生加载器）。
 * 生成的指令序列与 BBS 原生 Blender 导出器一致：
 * {@code o} / {@code o_arm} / {@code v} / {@code vw} / {@code vt} / {@code vn} / {@code f}
 * / {@code arm_name} / {@code arm_bone}。</p>
 *
 * <p>矩阵约定：.bbsm 中 4x4 矩阵一律列主序（JOML {@code get(float[])}）；
 * .bobj 的 {@code arm_bone} 指令按行主序书写（加载器 {@code set()+transpose()} 语义）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BOBJWriter
{
    /**
     * BOBJData → geometry JSON 段
     *
     * <p>结构：
     * {@code vertices: [[x,y,z],...]}，{@code vertex_weights: [[[bone,w],...],...]}（与顶点平行），
     * {@code uvs: [[u,v],...]}，{@code normals: [[x,y,z],...]}，
     * {@code meshes: [{name, armature, triangles: [p,t,n, p,t,n, p,t,n, ...]}]}（-1 表示缺索引）</p>
     */
    public static JsonObject geometryToJson(BOBJLoader.BOBJData data)
    {
        JsonObject geometry = new JsonObject();

        JsonArray vertices = new JsonArray();

        for (BOBJLoader.Vertex vertex : data.vertices)
        {
            vertices.add(vec3(vertex.x, vertex.y, vertex.z));
        }

        geometry.add("vertices", vertices);

        JsonArray weights = new JsonArray();

        for (BOBJLoader.Vertex vertex : data.vertices)
        {
            JsonArray vertexWeights = new JsonArray();

            for (BOBJLoader.Weight weight : vertex.weights)
            {
                JsonArray pair = new JsonArray();

                pair.add(weight.name);
                pair.add(weight.factor);
                vertexWeights.add(pair);
            }

            weights.add(vertexWeights);
        }

        geometry.add("vertex_weights", weights);

        JsonArray uvs = new JsonArray();

        for (Vector2d uv : data.textures)
        {
            JsonArray coords = new JsonArray();

            coords.add(uv.x);
            coords.add(uv.y);
            uvs.add(coords);
        }

        geometry.add("uvs", uvs);

        JsonArray normals = new JsonArray();

        for (Vector3f normal : data.normals)
        {
            normals.add(vec3(normal.x, normal.y, normal.z));
        }

        geometry.add("normals", normals);

        JsonArray meshes = new JsonArray();

        for (BOBJLoader.BOBJMesh mesh : data.meshes)
        {
            JsonObject meshJson = new JsonObject();

            meshJson.addProperty("name", mesh.name);
            meshJson.addProperty("armature", mesh.armatureName == null ? "" : mesh.armatureName);
            meshJson.addProperty("material", mesh.name);

            JsonArray triangles = new JsonArray();

            for (BOBJLoader.Face face : mesh.faces)
            {
                for (BOBJLoader.IndexGroup group : face.idxGroups)
                {
                    triangles.add(group.idxPos);
                    triangles.add(group.idxTextCoord);
                    triangles.add(group.idxVecNormal);
                }
            }

            meshJson.add("triangles", triangles);
            meshes.add(meshJson);
        }

        geometry.add("meshes", meshes);

        return geometry;
    }

    /**
     * BOBJArmature → skeleton JSON 段
     *
     * <p>结构：{@code name}，{@code bones: [{name, parent, index, head: [x,y,z],
     * bind_matrix: [16 列主序], inverse_bind_matrix: [16 列主序]}]}。
     * head 取自绑定矩阵平移分量（BOBJ 不保留骨骼尾端）。</p>
     */
    public static JsonObject skeletonToJson(BOBJArmature armature)
    {
        JsonObject skeleton = new JsonObject();

        skeleton.addProperty("name", armature.name == null ? "Armature" : armature.name);

        JsonArray bones = new JsonArray();

        for (BOBJBone bone : armature.orderedBones)
        {
            JsonObject boneJson = new JsonObject();

            boneJson.addProperty("name", bone.name);
            boneJson.addProperty("parent", bone.parent == null ? "" : bone.parent);
            boneJson.addProperty("index", bone.index);

            if (bone.boneMat != null)
            {
                JsonArray head = new JsonArray();

                head.add(bone.boneMat.m30());
                head.add(bone.boneMat.m31());
                head.add(bone.boneMat.m32());
                boneJson.add("head", head);
                boneJson.add("bind_matrix", matrixToJson(bone.boneMat));
            }

            boneJson.add("inverse_bind_matrix", matrixToJson(bone.invBoneMat));
            bones.add(boneJson);
        }

        skeleton.add("bones", bones);

        return skeleton;
    }

    /**
     * 矩阵 → 16 元素列主序 JSON 数组（null 输出单位阵）
     */
    public static JsonArray matrixToJson(Matrix4f matrix)
    {
        JsonArray array = new JsonArray();

        if (matrix == null)
        {
            for (int i = 0; i < 16; i++)
            {
                array.add(i == 0 || i == 5 || i == 10 || i == 15 ? 1F : 0F);
            }

            return array;
        }

        float[] values = new float[16];

        matrix.get(values);

        for (float value : values)
        {
            array.add(value);
        }

        return array;
    }

    /**
     * geometry + skeleton JSON 段 → .bobj 文本（外部 .bbsm 导入的兜底合成）
     */
    public static String writeBOBJ(JsonObject geometry, JsonObject skeleton)
    {
        StringBuilder builder = new StringBuilder(64 * 1024);

        /* 几何：v / vw / vt / vn，然后 o / o_arm / f（同一全局索引空间） */
        if (geometry != null)
        {
            JsonArray vertices = getArray(geometry, "vertices");
            JsonArray weights = getArray(geometry, "vertex_weights");
            JsonArray uvs = getArray(geometry, "uvs");
            JsonArray normals = getArray(geometry, "normals");
            JsonArray meshes = getArray(geometry, "meshes");

            if (vertices != null)
            {
                for (int i = 0; i < vertices.size(); i++)
                {
                    JsonArray vertex = vertices.get(i).getAsJsonArray();

                    builder.append("v ").append(floatString(vertex.get(0).getAsFloat()))
                        .append(" ").append(floatString(vertex.get(1).getAsFloat()))
                        .append(" ").append(floatString(vertex.get(2).getAsFloat()))
                        .append("\n");

                    if (weights != null && i < weights.size() && weights.get(i).isJsonArray())
                    {
                        for (JsonElement weight : weights.get(i).getAsJsonArray())
                        {
                            JsonArray pair = weight.getAsJsonArray();

                            if (pair.size() >= 2 && pair.get(1).getAsFloat() > 0.001F)
                            {
                                builder.append("vw ").append(pair.get(0).getAsString())
                                    .append(" ").append(floatString(pair.get(1).getAsFloat()))
                                    .append("\n");
                            }
                        }
                    }
                }
            }

            if (uvs != null)
            {
                for (JsonElement element : uvs)
                {
                    JsonArray coords = element.getAsJsonArray();

                    builder.append("vt ").append(floatString(coords.get(0).getAsFloat()))
                        .append(" ").append(floatString(coords.get(1).getAsFloat()))
                        .append("\n");
                }
            }

            if (normals != null)
            {
                for (JsonElement element : normals)
                {
                    JsonArray coords = element.getAsJsonArray();

                    builder.append("vn ").append(floatString(coords.get(0).getAsFloat()))
                        .append(" ").append(floatString(coords.get(1).getAsFloat()))
                        .append(" ").append(floatString(coords.get(2).getAsFloat()))
                        .append("\n");
                }
            }

            if (meshes != null)
            {
                for (JsonElement element : meshes)
                {
                    JsonObject mesh = element.getAsJsonObject();

                    builder.append("o ").append(getString(mesh, "name", "mesh")).append("\n");

                    String armature = getString(mesh, "armature", "");

                    if (!armature.isEmpty())
                    {
                        builder.append("o_arm ").append(armature).append("\n");
                    }

                    JsonArray triangles = getArray(mesh, "triangles");

                    if (triangles != null)
                    {
                        for (int i = 0; i + 8 < triangles.size(); i += 9)
                        {
                            builder.append("f");

                            for (int corner = 0; corner < 3; corner++)
                            {
                                int p = triangles.get(i + corner * 3).getAsInt() + 1;
                                int t = triangles.get(i + corner * 3 + 1).getAsInt();
                                int n = triangles.get(i + corner * 3 + 2).getAsInt();

                                builder.append(" ").append(formatFaceCorner(p, t, n));
                            }

                            builder.append("\n");
                        }
                    }
                }
            }
        }

        /* 骨骼：arm_name / arm_bone */
        if (skeleton != null && skeleton.has("bones") && skeleton.get("bones").isJsonArray())
        {
            builder.append("arm_name ").append(getString(skeleton, "name", "Armature")).append("\n");

            for (JsonElement element : skeleton.getAsJsonArray("bones"))
            {
                JsonObject bone = element.getAsJsonObject();
                JsonArray bind = getArray(bone, "bind_matrix");
                float[] columnMajor = new float[16];

                if (bind != null && bind.size() == 16)
                {
                    for (int i = 0; i < 16; i++)
                    {
                        columnMajor[i] = bind.get(i).getAsFloat();
                    }
                }
                else
                {
                    columnMajor[0] = columnMajor[5] = columnMajor[10] = columnMajor[15] = 1F;
                }

                JsonArray head = getArray(bone, "head");

                float hx = head != null && head.size() >= 3 ? head.get(0).getAsFloat() : 0F;
                float hy = head != null && head.size() >= 3 ? head.get(1).getAsFloat() : 0F;
                float hz = head != null && head.size() >= 3 ? head.get(2).getAsFloat() : 0F;

                builder.append("arm_bone ").append(getString(bone, "name", "bone"))
                    .append(" ").append(getString(bone, "parent", ""))
                    .append(" ").append(floatString(hx))
                    .append(" ").append(floatString(hy))
                    .append(" ").append(floatString(hz));

                /* 行主序书写：加载器 set(列主序) + transpose() 后还原原矩阵 */
                for (int row = 0; row < 4; row++)
                {
                    for (int col = 0; col < 4; col++)
                    {
                        builder.append(" ").append(floatString(columnMajor[col * 4 + row]));
                    }
                }

                builder.append("\n");
            }
        }

        return builder.toString();
    }

    /**
     * 面角点索引（1 基）：有 UV 与法线写 {@code p/t/n}，缺失部分省略
     */
    private static String formatFaceCorner(int pos, int tex, int norm)
    {
        if (tex < 0 && norm < 0)
        {
            return Integer.toString(pos);
        }

        if (norm < 0)
        {
            return pos + "/" + (tex + 1);
        }

        if (tex < 0)
        {
            return pos + "//" + (norm + 1);
        }

        return pos + "/" + (tex + 1) + "/" + (norm + 1);
    }

    private static JsonArray vec3(float x, float y, float z)
    {
        JsonArray array = new JsonArray();

        array.add(x);
        array.add(y);
        array.add(z);

        return array;
    }

    private static String floatString(float value)
    {
        return Float.toString(value);
    }

    private static String getString(JsonObject object, String key, String fallback)
    {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static JsonArray getArray(JsonObject object, String key)
    {
        return object.has(key) && object.get(key).isJsonArray() ? object.getAsJsonArray(key) : null;
    }
}
