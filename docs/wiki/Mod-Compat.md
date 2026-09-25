# Mod 兼容环境

AI 工具 → Mod 兼容环境。自动识别已安装 mod 并让 AI 理解它们。

- **识别来源**：Fabric Loader 元数据 + 注册表统计（物品/方块/实体数量与实体 ID 样例）+ 内置知识库（30+ 主流开源 mod 的拍摄视角知识）+ 关键词自动分类
- **适配器**：内置 5 个（实体演员库/Create 机械/GeckoLib 骨骼动画/玩家动画/光影渲染），零硬依赖，可在 AI 设置中启停
- **AI 上下文**：分镜生成与 AI 编辑器的提示词自动携带环境摘要（重点 mod 拍摄建议、可用演员实体）
- **报告**：`config/bbs/ai/mods_report.json`
- **扩展**：`AICore.registerModKnowledge()` / `registerModAdapter()` / `registerContextProvider()`
