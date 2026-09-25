# BBS AI Studio · bbs-fs 2.6 适配说明

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
