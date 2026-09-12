# BBS AI Studio 人物模型导出/导入系统（.bbsm）

> 对应需求：后续要求 1 —— 人物模型导出/导入系统；**导出时单文件导出和导入**。

## 一、总览

`.bbsm`（BBS AI Studio Model）是单文件人物模型封装格式：**GZIP + JSON**，
一个文件包含几何、骨骼、纹理、姿态、形态键、IK、物理与动作的全部数据。
游戏内导出、游戏内导入、Blender 外部导入三条路径共用同一格式。

```
游戏内导出 (ModelForm) ──┐
                         ├──►  .bbsm 单文件  ──►  游戏内导入（模型浏览器）
Blender 插件导出 ────────┤        (GZIP+JSON)          │
                         │                            ▼
Blockbench .bbs.json ────┴──（导入器直读）    config/bbs/assets/models/<id>/
```

## 二、文件结构

```jsonc
{
  "format": "bbs_ai_studio_model_v1",
  "metadata": {
    "name": "模型名称", "author": "作者", "description": "描述",
    "version": "1.0", "created_at": "ISO-8601", "source": "bbs_ai_studio|blender",
    "tags": []
  },
  "model_id": "原 ModelForm.model 模型 ID",
  "form": { /* FormUtils.toData(ModelForm) 全量序列化（含姿态/IK/物理/动作） */ },
  "model_files": {                       // 原始模型目录内嵌文本文件
    "model.bobj":     { "encoding": "text", "content": "…" },
    "config.json":    { "encoding": "text", "content": "…" },
    "shape_keys.json":{ "encoding": "text", "content": "…" }
  },
  "textures": [                          // Base64 内嵌纹理
    { "role": "main", "path": "models/foo/model.png",
      "encoding": "base64", "content": "…", "size": 12345 },
    { "role": "material:Head", "path": "models/foo/Head/head.png", … }
  ],
  "geometry": {                          // 解析后的几何（外部工具可直读）
    "vertices": [[x,y,z], …],
    "vertex_weights": [[[骨骼名, 权重], …], …],   // 与顶点平行
    "uvs": [[u,v], …], "normals": [[x,y,z], …],
    "meshes": [ { "name": "…", "armature": "…", "material": "…",
                  "triangles": [p,t,n, p,t,n, p,t,n, …] } ]   // 0 基，-1 缺省
  },
  "skeleton": {                          // 骨骼层级（4x4 矩阵列主序 16 元素）
    "name": "Armature",
    "bones": [ { "name": "…", "parent": "…", "index": 0,
                 "head": [x,y,z],
                 "bind_matrix": [16], "inverse_bind_matrix": [16] } ]
  },
  "pose": { "pose": { 骨骼名: PoseTransform } },   // Pose.toData()
  "shape_keys": { "keys": { 名称: 权重 } },         // ShapeKeys.toData()
  "ik": { … },                                      // ModelIKIO 的 MapType
  "physics": { … },                                 // ModelPhysicsIO 的 MapType
  "actions": { … }                                  // ActionsConfig.toData()
}
```

矩阵约定：`.bbsm` 内一律**列主序**（JOML `Matrix4f.get(float[])`）；
`.bobj` 的 `arm_bone` 指令按**行主序**书写（加载器 `set()+transpose()` 语义）。

## 三、目录约定

| 目录 | 用途 |
|------|------|
| `config/bbs/models/` | 游戏内导出的 .bbsm 输出 |
| `config/bbs/imports/models/` | 外部工具投放 .bbsm / .bbs.json / .bobj 的导入目录 |
| `config/bbs/assets/models/<id>/` | 导入解包目标（model.bobj / 纹理 / config.json） |

## 四、游戏内工作流

- **导出**（`Ctrl+Shift+E` 或 AI 面板 → 模型 → 导出）：
  当前选中角色（影片面板 → 回放编辑器 → Replay 表单）的 ModelForm →
  `ModelExporter.export` → `config/bbs/models/<名称>.bbsm`。
- **导入**（`Ctrl+Shift+O` / `Ctrl+Alt+M` 或 模型浏览器）：
  `ModelImporter.importFromFile` 解包到 `assets/models/<唯一ID>/`，重建
  ModelForm（`FormUtils.fromData`），重定向模型 ID 与纹理链接，应用到当前
  Replay（编辑器即时渲染即为预览，可用编辑器撤销回退）。

导入优先级：内嵌 `model.bobj` 原样写出（零损失往返）；外部 .bbsm 无
model.bobj 时由 `BOBJWriter.writeBOBJ(geometry, skeleton)` 现场合成。
`.bbs.json`（Blockbench）与裸 `.bobj` 直接复制进模型目录并生成最小表单。

## 五、Blender 插件

`bbs-ai-toolchain/blender_addon/bbs_ai_studio_exporter.py`

安装后 File → Export → **BBS AI Studio Model (.bbsm)**。导出内容：
网格（世界坐标 + 顶点权重 + UV + 法线）、骨骼架（含绑定/逆绑定矩阵）、
当前姿态、IK 约束、材质纹理（内嵌 PNG）。同时现场生成 `model.bobj`
（`model_files.model.bobj`），游戏内无需额外转换。

坐标变换：Blender（Z 朝上）→ BBS（Y 朝上）：`bbs = (x, -z, y)`，
绑定矩阵做基变换 `M' = A·M·A⁻¹`。

## 六、代码清单

| 文件 | 说明 |
|------|------|
| `src/main/java/mchorse/bbs_ai/model/BBSSModel.java` | 统一格式数据结构 + GZIP/JSON 编解码 |
| `src/main/java/mchorse/bbs_ai/model/DataJson.java` | BBS 数据树（MapType 等）↔ Gson 转换 |
| `src/main/java/mchorse/bbs_ai/model/BOBJWriter.java` | BOBJData → geometry/skeleton JSON；JSON → .bobj 文本 |
| `src/main/java/mchorse/bbs_ai/model/ModelExporter.java` | 游戏内导出器（ModelForm → .bbsm） |
| `src/main/java/mchorse/bbs_ai/model/ModelImporter.java` | 游戏内导入器（.bbsm/.bbs.json/.bobj → ModelForm） |
| `src/main/java/mchorse/bbs_ai/model/ModelEntry.java` | 模型浏览器条目 |
| `src/main/java/mchorse/bbs_ai/model/ModelBrowser.java` | 三目录扫描 + 关键字过滤 |
| `src/client/java/mchorse/bbs_ai/ui/model/ExportModelPanel.java` | 导出对话框 |
| `src/client/java/mchorse/bbs_ai/ui/model/ModelBrowserPanel.java` | 模型浏览器对话框 |
| `src/client/java/mchorse/bbs_ai/ui/model/ModelFormUI.java` | 定位/应用当前 ModelForm |
| `bbs-ai-toolchain/blender_addon/bbs_ai_studio_exporter.py` | Blender 导出插件 |

## 七、热键

| 动作 ID | 默认按键 | 说明 |
|---------|----------|------|
| `model.export` | `Ctrl+Shift+E` | 导出当前人物模型 |
| `model.import` | `Ctrl+Shift+O` | 打开模型浏览器导入（需求原文的 Ctrl+Shift+M 已被 `ai.recognize` 占用，故改用 O） |
| `model.browser` | `Ctrl+Alt+M` | 模型浏览器 |

## 八、与预烘焙预览系统的关系

模型导入不做独立的「预烘焙预览」——BBS 编辑器对 Replay 表单的即时渲染
本身就是预览；导入直接 `Replay.form.set(form)` 生效，回退走编辑器撤销。
动作数据的预览/烘焙（`PreviewSystem.stage/bake/discard`）不受本系统影响，
两者通过 Replay 通道（`pose.bones.*`）天然互通。
