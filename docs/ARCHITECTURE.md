# BBS AI Studio 架构总览

> 面向维护者的结构地图。读完这篇，你应该能定位任何功能的代码位置，
> 并理解"AI 生成 → 预览 → 烘焙"这条主数据流。

## 一、顶层布局

```
bbs-ai-studio/
├── src/main/java/mchorse/
│   ├── bbs_mod/          # 上游 BBS FS 原版代码 —— 原则上不改；改动必须标注【原版兼容】
│   └── bbs_ai/           # ★ 本 Fork 的 AI 模块（所有新功能都在这个包里）
├── src/client/java/mchorse/
│   ├── bbs_mod/          # 上游客户端代码（UI 框架、渲染、L10n……）
│   └── bbs_ai/           # ★ AI 模块的客户端部分（面板/主题/热键/操作模式/Gizmo）
├── src/main/resources/assets/bbs/assets/
│   ├── strings/bbs_ai_*.json   # AI 模块三语文件（Link.assets 解析根在 assets/bbs/assets/，勿移）
│   └── ai_themes/              # 内置主题模板（首次运行解压到 config/bbs/ai_themes/）
├── bbs-ai-toolchain/     # ★ 外部 Python 工具链（独立进程，与本 Mod 通过 JSON 契约互通）
├── docs/                 # 维护文档（架构 / 2.6 适配 / 维护手册）
└── .github/workflows/    # CI：分支构建验证；v* 标签 → jar + exe 自动发 Release
```

**为什么 AI 代码不放独立 sourceSet？** Fabric Loom 的 split source sets 与 remap
按路径绑定，物理拆分会牵动构建配置；包名隔离（`mchorse.bbs_ai` vs `mchorse.bbs_mod`）
已足够划清边界，且每个包都有 `package-info.java` 说明职责。

## 二、主源集包索引（mchorse.bbs_ai）

| 包 | 职责 | 关键类 |
|---|---|---|
| `core` | AI 服务总控、AES-GCM 加密配置、全部设置注册 | `AIServiceManager` `ConfigSyncManager` `BBSAISettings` `EncryptionUtil` |
| `core.api` | 厂商 API 抽象 + OkHttp 实现（OpenAI 兼容 / Anthropic / 视觉） | `AIProvider` `InGameAPIProvider` `APIResponseParser` |
| `format` | `bbs_ai_studio_motion_v1` 统一数据契约（与 Python 端字段级一致） | `MotionData` `MotionFrame` `BonePose` |
| `motion` | 视频识别四件套：抽帧 → ONNX 推理 → 骨骼映射 → 关键帧 | `VideoFrameExtractor` `LocalPoseEstimator` `SkeletonMapper` `MotionKeyframeGenerator` |
| `storyboard` | 文本分镜 DSL、提示词工程、→ Film 转换 | `StoryboardScript` `StoryboardPromptBuilder` `StoryboardToFilmConverter` |
| `ik` | Blender IK：约束参数、CCD 解算（锚点跟随/极向/限制/拉伸）、Form 集成 | `BlenderIKConstraint` `BlenderIKSolver` `BlenderIKComponent` |
| `preview` | 预烘焙预览数据层：暂存/缓存/冲突检测/烘焙写入 | `PreviewSystem` `PrebakeCache` `PreviewTrack` `BakeTarget` |
| `import_manager` | 扫描+监听 `ai_cache/` 与 `imports/` 两个目录 | `ImportManager` `ImportEntry` |
| `integration` | Fabric 入口点、`/bbs_ai` 命令、addon 事件 | `BBSAIStudioMod` `BBSAICommands` `event/*` |
| `ui` | 主题/语言**枚举**（管理器在 client 侧） | `UITheme` `UILanguage` |

## 三、客户端包索引（client 源集 mchorse.bbs_ai）

| 包 | 职责 | 关键类 |
|---|---|---|
| `integration` | ClientModInitializer + bbs-client-addon（L10n/面板注册） | `BBSAIStudioModClient` `BBSAIStudioAddon` |
| `ui.panel` | 仪表盘主面板：六区块 + 状态栏（导入/视频/分镜/设置/IK/界面） | `UIAIToolsPanel` |
| `ui.theme` | 三主题管理与布局引擎（经典/Blender/Mine-imator） | `ThemeManager` `ThemeResourcePack` `layouts/*` |
| `ui.language` | 三语切换与 CJK 度量适配 | `LanguageManager` `FontManager` |
| `ui.hotkey` | 50+ 热键注册/重绑定/冲突检测、状态栏、F1 速查、新手引导 | `HotkeyRegistry` `HotkeySettingsPanel` `StatusBar` `FirstTimeGuide` |
| `ui.transform` | Blender G/R/S 变换、视口导航、时间轴、Mine-imator Gizmo | `BlenderTransformSystem` `BlenderViewportController` `MineimatorTransformSystem` |
| `preview` | 洋葱皮 HUD、烘焙确认对话框 | `PreviewRenderer` `BakeConfirmationDialog` |
| `ik` | 屏幕空间 Gizmo、约束面板 | `BlenderIKGizmo` `BlenderIKSettingsPanel` |
| `motion` | 识别流水线客户端编排（后台线程串联全流程） | `VideoRecognitionPipeline` |

## 四、主数据流（务必理解）

```
                 ┌────────────────────────────────────────────┐
  视频文件 ──FFmpeg──▶ 抽帧 ──ONNX──▶ 姿态 ──映射──▶ 关键帧      │
                 │  (motion)                    (MotionData)  │
  用户文本 ──API──▶ 分镜 DSL ──转换──▶ 相机剪辑+角色关键帧      │
                 │  (storyboard)                             │
  外部工具链 ──────── config/bbs/imports/*.json ──扫描──▶     │
                 └──────────────┬─────────────────────────────┘
                                ▼
                 PreviewSystem.stage()  【内存缓存，不碰正式数据】
                                ▼
        PreviewRenderer（洋葱皮 HUD） + UIAIToolsPanel（调整参数）
                                ▼
                 BakeConfirmationDialog（冲突提示）
                     │ 覆盖 / 插入Blend        │ 取消
                     ▼                          ▼
        写入 Film（BakeTarget，两条路径）     PrebakeCache.discard()
        · 客户端：仪表盘当前编辑中的 Film
        · 离线：FilmManager 落盘
```

约定：**任何生成器的产出都必须先进 `PreviewSystem.stage()`**，禁止绕过预览直写 Film。

## 五、外部工具链（bbs-ai-toolchain/）

```
core/
├── motion_data.py      # 数据契约（与 Java format 包字段一致，改动需两侧同步！）
├── video_reader.py     # OpenCV 视频读取/抽帧
├── pose_estimator.py   # MediaPipe / YOLOv8 / RTMPose 三后端
├── skeleton_mapper.py  # 骨骼映射 + OneEuro 平滑 + Foot Lock
├── exporter.py         # 导出到 config/bbs/imports/
├── config_loader.py    # 解密读取游戏内共享配置（AES 契约见 EncryptionUtil）
├── api_provider.py     # API 抽象 + openai/claude/deepseek/glm 适配器
└── batch_processor.py  # 批量队列（暂停/继续/取消）
main.py / cli.py        # Gradio GUI / 命令行
requirements.txt        # 运行依赖（mediapipe 钉 0.10.21）
requirements-build.txt  # 打包依赖（exe 不内置 mediapipe，走 rtmpose）
bbs_ai_toolchain.spec   # PyInstaller 配置（GUI+CLI 双 exe）
```

## 六、关键集成点（都是【原版兼容】约束的位置）

| 集成点 | 方式 |
|---|---|
| 模初始化 | 独立 Fabric 入口点 `BBSAIStudioMod`（不修改 `BBSMod.onInitialize`） |
| 设置 | `BBSMod.setupConfig(...)` + `SettingsBuilder`（config/bbs/settings/bbs_ai.json） |
| 语言文件 | `BBSAIStudioAddon` 订阅 `RegisterL10nEvent`，重载前注册 |
| 面板 | 订阅 `RegisterDashboardPanelsEvent` 注册 `UIAIToolsPanel` |
| 骨骼动画写入 | `FormProperties.getOrCreate(form, "pose.bones.<bone>")`（键前缀内联于 PreviewSystem） |
| 2.6 前瞻兼容 | `PreviewSystem.collectChannels()` 反射探测 properties/tracks 字段 |

## 七、命名与注释约定

- 新代码一律 `mchorse.bbs_ai.*`；注释中文；类头带功能说明 + `作者：BBS AI Studio`；
- 涉及上游系统的代码必须写 `// 【原版兼容】`；
- 设置项键名 = `bbs_ai.config.<分类>.<id>`（+`-comment`），三语文件三处同步；
- 面板文案走 `L10n.lang("bbs_ai.panel.*")`，不硬编码。
