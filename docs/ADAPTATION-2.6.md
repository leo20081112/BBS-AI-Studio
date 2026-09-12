# BBS AI Studio · bbs-fs 2.6 适配说明

> **2026-09-13 更新：全部四个版本（1.20.4 / 1.20.1 / 1.21.1 / 1.21.11）已完成 2.6 重基。**
> 底座 `mchorse.bbs_mod`（main + client 源集）与上游 `dev` / `dev-1.20.1` / `dev-1.21.1` / `dev-1.21.11`
> 分支完全一致，`mod_version=2.6`。本文档保留为历史记录与迁移说明。

## 零、已完成的重基记录（2026-09-13）

- 底座替换：`src/main/java/mchorse/bbs_mod`、`src/client/java/mchorse/bbs_mod`、
  `src/main/resources`、`src/client/resources`、`src/test`、`api/bbs-api.txt`、`docs/addon-template`。
- 构建合并：`build.gradle` 加入上游的 `test` / `migrationTest` / `apiDump` / `apiCheck` 任务；
  `gradle.properties` 升至 `mod_version=2.6`。
- 保留的自定义底座补丁（唯一一处）：`BBSRendering.java` 的 Iris `IrisUtils.setup()` 优雅降级
  （Iris 版本不兼容时禁用着色器集成而不是崩溃），四个版本均已重新应用。
- AI 侧 2.6 迁移点（见下文第二节，均已落地）：
  - `BBSAIStudioAddon` 改用 `mchorse.bbs_mod.api.BBSAddonMod` / `api.Subscribe` /
    `api.client.events.RegisterL10nEvent` / `api.client.events.RegisterDashboardPanelsEvent`；
  - `UIAIToolsPanel` 的 `appear()` 逻辑改为构造期 `onAppear(...)` 回调（2.6 中面板生命周期方法为 final）；
  - `ModelExporter` / `ModelImporter` 移除对 `ModelForm.ik` / `ModelForm.physics` 的引用
    （2.6 起 IK 随表单 `bones` 区块序列化，物理与约束存于模型 config.json）。
- 修复的既有 bug：
  - AI 语言 / 主题资源曾放在 `assets/bbs/strings`、`assets/bbs/ai_themes`（少一层 `assets`，
    `Link.assets` 解析根为 `assets/bbs/assets`），已移正——此前语言显示原始 key、主题模板无法提取；
  - `fabric.mod.json` 的 `bbs-client-addon` 入口点一度被底座覆盖操作冲掉，已恢复；
  - 模型导出/导入系统（10 个类）已从主版本移植到其余三个版本，四个版本 bbs_ai 代码哈希级一致。

---

# 以下为重基前的前瞻兼容记录

> 上游 bbs-fs 2.6 位于 `dev` / `dev-1.21.1` 分支（471 个提交、300 个文件改动），
> 尚未发布稳定版。本文档记录本 Fork（基于 2.5.2）与 2.6 的 API 差异、
> 已完成的前瞻兼容、以及未来重基 2.6 时必须修改的位置。

## 一、已实现的双版本兼容（无需动作）

| 触点 | 2.5.2 | 2.6 | 处理方式 |
|---|---|---|---|
| 骨骼通道遍历（冲突检测） | `FormProperties.properties`（Map<String, Channel>） | 改名 `tracks`（Map<TrackId, Channel>） | `PreviewSystem.collectChannels()` 反射按字段名探测，两个版本都工作 |
| 骨骼通道键 | `PerLimbService.POSE_BONES`（"pose.bones."） | `PerLimbService` 类移除 | `PreviewSystem.POSE_BONES_PREFIX` 自带常量，无依赖 |
| 骨骼通道写入 | `FormProperties.getOrCreate(Form, String)` | 保留（新增 TrackId 重载） | 直接兼容 |
| SettingsBuilder / ValueInt / BaseValue | — | 仅加法演进（BaseValue 新增 `synced()`，IValueListener 新增 `FLAG_BATCH`） | 直接兼容 |
| KeyframeChannel / PoseTransform / Film.camera+replays / Films.playFilm 系 / BBSMod.setupConfig / Icons | — | 签名不变 | 直接兼容 |

## 二、重基 2.6 时必须修改的位置

1. **`BBSAIStudioAddon`**（client）
   - `RegisterL10nEvent` 与 `RegisterDashboardPanelsEvent` 在 2.6 中已移除（包不存在）。
   - 2.6 的语言注册与面板注册改走新的入口（见 dev 分支 `BBSModClient` 与 `UIDashboard`）。
   - 迁移：重基时把 addon 的两个 `@Subscribe` 方法改为 2.6 的新注册方式。

2. **`UIAIToolsPanel`**
   - `UIDashboardPanel` 在 2.6 中把 `open/close/appear/disappear` 变为 `final`，
     子类需在构造期用 `onAppear(() -> ...)` / `onDisappear(() -> ...)` 注册回调。
   - 迁移：`appear()` 里的首次引导逻辑挪到构造期 `onAppear(...)`。

3. **`PerLimbService`**（若直接引用）
   - 2.6 中类被移除。本 Fork 已内联 `"pose.bones."` 常量，无需动作；
     未来新增代码不要 import 该类。

## 三、版本探测（可选）

需要按 BBS 版本分支行为时：

```java
String bbsVersion = FabricLoader.getInstance()
    .getModContainer("bbs")
    .map(c -> c.getMetadata().getVersion().getFriendlyString())
    .orElse("unknown");
```

## 四、2.6 值得关注的新能力（重基后可接入）

- `cubic/ik` 重构（8 个文件）与 `cubic/constraints`（3 个文件）：可与 BlenderIKComponent 对接原生骨骼约束。
- `fonts/FontManager`：CJK 字体渲染可对接我们的 FontManager 度量适配。
- `client/api/client`（21 个新文件）：面向 addon 的客户端 API 层，未来 AI 面板可迁移到官方 API。
- `FilmMarkers` / `Film.calculateDuration()`：分镜转换可写入镜头标记，改善时间轴可读性。
