# BBS AI Studio

**BBS AI Studio** 是 [BBS FS](https://github.com/Wemppy4/bbs-fs)（Fabric 1.21.11 的 Minecraft 动画 Mod）的 Fork，在完全兼容原版 BBS 插件生态的前提下，新增 **AI 驱动的动画制作能力**，并重构 UI/UX 体验。

> English documentation: [README.md](README.md)。

## 架构总览（双轨 × 双模式）

```
┌──────────────────────────────────────────────────────────────────┐
│                        BBS AI Studio                             │
│   路径 A：游戏内（实时/轻量）      路径 B：外部工具链（高性能/批量）  │
│   ├ 本地模式（ONNX）              ├ 本地模式（Python）             │
│   ├ API 模式（云端大模型）        ├ API 模式（云端大模型）          │
│            ↓                            ↓                        │
│        ┌────────── 统一预烘焙预览系统（洋葱皮 + 缓存 + 确认烘焙）──┐ │
│        └──────────────→ 正式写入 Film 系统【原版兼容】←──────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

## 功能模块（13 个）

| # | 模块 | 说明 |
|---|------|------|
| 1 | AI 核心配置 | 厂商配置（OpenAI / Claude / DeepSeek / GLM / 自定义），AES-GCM 加密存储，SettingsBuilder 注册【原版兼容】 |
| 2 | 游戏内本地 AI | FFmpeg 抽帧、ONNX 姿态估计（YOLOv8-pose / RTMPose）、COCO-17 → 6 骨骼映射、OneEuro 平滑、脚部锁定 |
| 3 | 游戏内 API 模式 | OkHttp 客户端，OpenAI 兼容 + Anthropic 双协议，视觉输入，完整错误映射 |
| 4 | 云端分镜生成 | 严格 JSON 分镜 DSL（镜头 / 角色 / 转场）→ BBS Film 转换【原版兼容】 |
| 5 | 外部工具链 | Python + Gradio GUI + CLI：MediaPipe / YOLO / RTMPose 多后端、批量队列、配置解密共享 |
| 6 | 统一导入管理器 | 监听 `config/bbs/ai_cache/` 与 `config/bbs/imports/`，区分游戏内 / 外部来源 |
| 7 | 预烘焙预览系统 | 暂存 → 内存缓存（FloatBuffer）→ 洋葱皮 HUD → 烘焙确认对话框 → 写入 Film |
| 8 | Blender IK | 完整约束数据模型 + CCD 解算器（锚点跟随 / 极向目标 / 链长 / 旋转限制 / 拉伸 / 权重衰减） |
| 9 | UI 主题系统 | 经典 BBS / Blender 深色 / Mine-imator 三套布局与主题资源包 |
| 10 | 操作逻辑 | BBS 兼容（默认）、Blender G/R/S + 视口导航、Mine-imator Gizmo 风格 |
| 11 | 快捷键系统 | 50+ 热键注册、自定义重绑定（冲突检测）、状态栏、F1 速查表、新手引导 |
| 12 | 语言系统 | English / 简体中文 / 繁體中文，CJK 字符度量适配 |
| 13 | 整合初始化 | Fabric 入口点、事件广播、`/bbs_ai` 命令树 |

## 构建

环境要求：JDK 21（Gradle 工具链）、Fabric Loom 1.15。

```bash
./gradlew build
```

产物在 `build/libs/`。OkHttp 与 ONNX Runtime 会自动打包（nested JARs）进 Mod jar。

## AI 功能使用

1. 将 Mod 安装到 Fabric 1.21.11 环境并启动游戏。
2. 打开 BBS 仪表盘（默认 `0` 键）→「AI 工具」面板。
3. **AI 设置**：选择厂商，粘贴 API Key（加密存于 `config/bbs/settings/ai_providers.json`），点击「测试连接」。
4. **分镜生成**：输入剧情描述 → 生成 → 自动创建包含相机剪辑与角色关键帧的影片。
5. **视频识别**：填写 `.mp4/.avi/.mov/.webm` 路径。需要：
   - 系统安装 FFmpeg（或用 `-Dbbs_ai.ffmpeg=/路径/ffmpeg` 指定）；
   - 将 ONNX 姿态模型放到 `config/bbs/ai_cache/models/`（`yolov8n-pose.onnx` 或 `rtmpose-m.onnx`）。
6. 所有生成结果**先进入预览**：洋葱皮检查 → `Ctrl+Enter` 烘焙 / `Ctrl+Esc` 放弃。

### 外部工具链

```bash
cd bbs-ai-toolchain
pip install -r requirements.txt
python main.py            # Gradio GUI（深色主题）
python cli.py convert dance.mp4 --backend mediapipe --mirror
python cli.py test-connection --uuid <你的 Minecraft UUID>
```

导出的 JSON 自动落盘 `config/bbs/imports/`，游戏内「导入管理」即时发现。

## `/bbs_ai` 命令

```
/bbs_ai generate storyboard <json>     把分镜 JSON 转换为影片
/bbs_ai preview enter|exit|bake        控制预览流水线
/bbs_ai ik mode <native|blender>       切换 IK 模式
/bbs_ai theme <classic|blender|mineimator>
/bbs_ai language <en_us|zh_cn|zh_tw>
/bbs_ai version
```

## 统一数据契约（bbs_ai_studio_motion_v1）

```json
{
  "format": "bbs_ai_studio_motion_v1",
  "metadata": { "source": "video", "fps": 30, "model": "mediapipe_pose" },
  "actor": { "target_form": "minecraft:player", "skeleton_type": "standard_6bone" },
  "keyframes": [
    { "tick": 0, "bones": { "head": { "rotation": [0.0, 0.0, 0.0], "interpolation": "linear" } } }
  ],
  "camera_shots": [],
  "actions": []
}
```

`tick` 为 Minecraft 时间（20 ticks = 1 秒）；rotation 为欧拉角度数 `[x, y, z]`。

## 目录结构

```
bbs-ai-studio/
├── src/main/java/mchorse/
│   ├── bbs_mod/        # 原版 BBS FS 代码（接口不动）
│   └── bbs_ai/         # AI 模块（本 Fork 新增）
│       ├── core/       # AI 服务总控、加密配置、设置注册
│       ├── core/api/   # 厂商 API 抽象 + OkHttp 实现
│       ├── format/     # bbs_ai_studio_motion_v1 数据契约
│       ├── motion/     # 视频 → 动作识别流水线（FFmpeg + ONNX）
│       ├── storyboard/ # 文本分镜 DSL → BBS Film 转换
│       ├── ik/         # Blender 级 IK 约束与解算
│       ├── preview/    # 预烘焙预览（暂存/缓存/烘焙）
│       ├── import_manager/ # 导入目录扫描与监听
│       └── integration/    # Fabric 入口点、事件、/bbs_ai 命令
├── src/client/java/mchorse/bbs_ai/   # 客户端 UI（面板/主题/热键/操作模式）
├── src/main/resources/assets/bbs/assets/
│   ├── strings/        # bbs_ai_*.json 三语语言文件
│   └── ai_themes/      # 内置主题模板
├── bbs-ai-toolchain/   # 外部 Python 工具链（详见其 README.md）
├── docs/               # ARCHITECTURE.md / MAINTENANCE.md / ADAPTATION-2.6.md
└── .github/workflows/  # CI（打 tag 自动发 jar + exe Release）
```

每个 `mchorse.bbs_ai` 包都带 `package-info.java` 职责说明；建议从
`docs/ARCHITECTURE.md`（架构与数据流）和 `docs/MAINTENANCE.md`（"改 X 要动哪"速查）读起。

## 开发约定

- 新代码全部位于 `mchorse.bbs_ai.*` 包，注释使用中文；
- 涉及原版 BBS 系统的代码标注 `// 【原版兼容】`；
- 所有设置项通过 `SettingsBuilder` 注册；
- API Key 一律 AES-GCM 加密落盘，禁止明文。

## 许可证

原版 BBS FS 采用 MIT 许可 —— 见 [LICENSE.md](LICENSE.md)，本 Fork 延续 MIT。

## 致谢

- [McHorse](https://github.com/mchorse) 与 [Wemppy](https://github.com/Wemppy4) —— BBS FS
- Blender Foundation —— IK 约束语义参考
- MediaPipe / Ultralytics / RTMPose —— 姿态估计模型
