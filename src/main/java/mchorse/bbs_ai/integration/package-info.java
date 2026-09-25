/**
 * 整合初始化与扩展点
 *
 * <p>主入口已重写进底层：{@code mchorse.bbs_mod.ai.AICore} 由 BBSMod.onInitialize 直接启动
 * （AI 不再以独立 Fabric 入口点外挂）。本包保留 {@link mchorse.bbs_ai.integration.BBSAICommands}
 * /bbs_ai 命令树；event 子包为供 addon 扩展的事件（AI 生成完成 / 预览进出 / IK 模式 / 主题 / 语言）。</p>
 */
package mchorse.bbs_ai.integration;
