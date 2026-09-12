# BBS AI Studio 维护手册

> 常见维护任务的"改动位置速查"。架构背景见 [ARCHITECTURE.md](ARCHITECTURE.md)。

## 1. 新增一个设置项

改动四处（三棵树同步）：

1. `mchorse/bbs_ai/core/BBSAISettings.java` —— 在对应分类方法里 `builder.getXxx(...)`，
   声明 `public static` 字段；如需变更回调，在 `register()` 尾部挂 `postCallback`。
2. 三语文件 `assets/bbs/assets/strings/bbs_ai_*.json` —— 加两条键：
   `bbs_ai.config.<分类>.<id>`（标题）与 `bbs_ai.config.<分类>.<id>-comment`（悬停提示）。
3. 需要下拉选项时：在 `BBSAIStudioModClient.onInitializeClient` 里对该字段调 `.modes(...)`。
4. `docs/ARCHITECTURE.md` 的设置清单无需更新（由键自描述）。

> 下拉模式的标签目前为中文常量；如需三语，改用 `L10n.lang(...)` 并补键。

## 2. 新增面板区块（AI 工具面板）

全部在 client 源集 `ui/panel/UIAIToolsPanel.java`：

1. 顶部 `icon(Icons.X, "bbs_ai.panel.tab.<id>", "<id>")` 加一个图标；
2. 写 `build<Name>Section()`，并在 `buildSection()` 的 switch 里加分支；
3. 三语文件加 `bbs_ai.panel.tab.<id>` 与区块内文案键；
4. 面板文案一律 `L10n.lang(...)`，不写死中文。

## 3. 新增一个 AI 厂商

1. `AIConfig.Provider` 枚举加一项（默认 Base URL + 模型名）；
2. `InGameAPIProvider.buildOpenAIUrl` 加端点拼接规则（若非 OpenAI 兼容协议，
   仿照 `requestClaude` 单独写请求/解析）；
3. 三语文件更新 `bbs_ai.config.ai.provider` 提示；
4. `BBSAIStudioModClient` 的 provider `.modes(...)` 标签会自动跟随枚举长度（上限 5，超出同步改设置注册的 min/max）。

## 4. 发版流程（全自动）

```bash
git tag v1.x.x-mc<版本>     # 在对应分支上（master=1.20.4 / 1.20.1 / 1.21.1）
git push origin v1.x.x-mc<版本>
```

CI 自动完成：Java 编译构建 → Python 语法+契约测试 → Windows exe 打包 →
创建 GitHub Release 并挂载 `*.jar` + `bbs-ai-toolchain-windows-x64.zip`。
无需手动上传任何产物。

## 5. 升级上游基座（如重基 bbs-fs 2.6）

1. 下载上游目标分支，确认 `gradle.properties` 与结构；
2. 按 `docs/ADAPTATION-2.6.md` 的清单迁移（当前仅两处编译期改动：
   addon 的两个事件注册、UIDashboardPanel 钩子模式）；
3. 叠加 `src/*/java/mchorse/bbs_ai`、`strings/`、`ai_themes/`、
   `bbs-ai-toolchain/`、`docs/`、`.github/`，改 fabric.mod.json 与 build.gradle
   （参考 1.21.1 分支的完整样例）；
4. 编译 → 冒烟（进游戏开 AI 面板）→ 推送新分支。

历史上 1.20.1 / 1.21.1 的移植各花费约 30 分钟（AI 代码零改动）。

## 6. 调试速查

| 症状 | 首查位置 |
|---|---|
| 视频识别报"未找到 FFmpeg" | 安装 FFmpeg 或 `-Dbbs_ai.ffmpeg=路径`；`VideoFrameExtractor.isFfmpegAvailable` |
| 视频识别报"未找到姿态模型" | `config/bbs/ai_cache/models/` 放 `yolov8n-pose.onnx` / `rtmpose-m.onnx` |
| 分镜生成报 401/429/超时 | `APIException.getUserMessage()` 已映射；检查 AI 面板"测试连接" |
| 设置界面显示原始键名 | 三语文件键格式必须为 `bbs_ai.config.<分类>.<id>`（语言切回中文验证） |
| 预览不出现 | 确认走了 `PreviewSystem.stage()` 且缓存非空（`/bbs_ai preview enter`） |
| 打包 exe 报 mediapipe 导入错误 | 打包版不内置 mediapipe，用 `--backend rtmpose --model <onnx>` |

## 7. 分支模型

| 分支 | MC 版本 | Java | 基座 |
|---|---|---|---|
| `master` | 1.20.4 | 17 | bbs-fs master (2.5.2) |
| `1.20.1` | 1.20.1 | 17 | bbs-fs 1.20.1 (2.5.2) |
| `1.21.1` | 1.21.1 | 21 | bbs-fs 1.21.1 (2.5.2) |
| `1.21.11` | 1.21.11 | 21 | bbs-fs 1.21.11 (2.5.2) |

**各分支的功能代码保持逐字一致**（除构建配置与 1.21.11 的按键分类/资源路径适配）；
任何修复/功能必须多树同步提交。
`bbs-fs 2.6` 在上游 dev 分支，重基评估见 [ADAPTATION-2.6.md](ADAPTATION-2.6.md)。
