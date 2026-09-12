/**
 * BBS AI Studio 主模块根包
 * 
 * <p>本包及子包是基于 BBS FS 的 AI 动画制作模块（上游代码在 {@link mchorse.bbs_mod}，
 * 两者互不侵入，仅通过 BBS 公开 API 集成）。根类 {@link mchorse.bbs_ai.BBSAIStudio}
 * 集中定义目录约定与版本常量。</p>
 * 
 * <p>包结构总览见 docs/ARCHITECTURE.md：</p>
 * <ul>
 *   <li>core —— AI 服务总控、加密配置、设置注册</li>
 *   <li>core.api —— AI 厂商 API 抽象与 OkHttp 实现</li>
 *   <li>format —— bbs_ai_studio_motion_v1 统一数据契约</li>
 *   <li>motion —— 视频 → 动作识别流水线（FFmpeg + ONNX）</li>
 *   <li>storyboard —— 文本分镜 DSL → BBS Film 转换</li>
 *   <li>ik —— Blender 风格 IK 约束与解算</li>
 *   <li>preview —— 预烘焙预览（暂存/缓存/烘焙）</li>
 *   <li>import_manager —— 导入目录扫描与监听</li>
 *   <li>integration —— Fabric 入口点、事件、命令</li>
 *   <li>ui —— 主题/语言数据模型（UI 主体在 client 源集同名包）</li>
 * </ul>
 */
package mchorse.bbs_ai;
