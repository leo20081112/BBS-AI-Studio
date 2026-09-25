# BBS AI Studio Wiki

> BBS FS 拍摄框架的 AI 驱动扩展。兼容上游 bbs-fs **2.5 / 2.6 / 2.7**，
> 支持 MC **1.20.1 / 1.20.4 / 1.21.1 / 1.21.11** 四个版本。

![AI 编辑器](images/ai-editor.png)

## 功能页

| 页面 | 内容 |
|---|---|
| [AI 编辑器](AI-Editor) | 专门的 AI 生成界面：画面窗口 + 影片时间轴（左），Codex 式对话 + 思维链（右），分镜/动作一键生成、可撤销 |
| [AI 工具面板](AI-Tools) | 原生内容辅助：AI 服务设置、视频骨骼识别、导入管理、人物模型、IK 调整、Mod 兼容环境 |
| [AI 设置](AI-Settings) | 服务商 / API Key / 生成参数 / 人物模型映射 / 插件调用（适配器开关） |
| [锚点跟随](Anchor-Follow) | 手/脚旋转时的锚点联动：脚部贴地、手部抓附，三层实现（约束面板 / 角色属性页 / 渲染链） |
| [Mod 兼容](Mod-Compat) | 自动识别已装 mod、内置知识库、AI 兼容适配器（Create/GeckoLib/Iris…）、选角推荐 |
| [各界面 AI 辅助](Per-Panel-Assist) | 影片/模型编辑/变形面板动作栏的 AI 按钮，上下文感知动作菜单 |
| [命令](Commands) | `/bbs_ai` 全部子命令（generate/preview/ik/theme/language/mods/undo/redo） |
| [版本兼容](Version-Compat) | 2.5/2.6/2.7 编译与运行时兼容层（BBSFSCompat） |

## 快速开始

1. 安装：把 `bbsfsai-<版本>-<MC>.jar` 放入 `mods/`，需要 Fabric API。
2. 配置：游戏内 `0` 键打开仪表盘 → AI 工具 → AI 设置，填入 API Key（OpenAI/Claude/DeepSeek/GLM/自定义）。
3. 生成：`Ctrl+Shift+S` 打开 AI 编辑器 → 右侧对话描述想法 → AI 回复中的代码块一键生成为影片/动作。
4. 撤销：任何 AI 操作 `Ctrl+Z` 可撤销。

## 数据与文件

| 路径 | 内容 |
|---|---|
| `config/bbs/settings/bbs_ai.json` | AI 设置（Key 加密存储于 ai_providers.json） |
| `config/bbs/ai/mods_report.json` | Mod 环境自动识别报告（外部工具链可读） |
| `config/bbs/ai_cache/` `config/bbs/imports/` | 动作数据导入目录 |
| `config/bbs_ai_bridge.token` | 调试桥令牌（127.0.0.1:28080） |

## 构建与调试

```
构建并调试.bat        # 构建 → 部署 → 启动（四版本分支各自独立）
```
