/**
 * AI 核心服务
 * 
 * <p>{@link mchorse.bbs_ai.core.AIServiceManager} 服务总控（Provider 生命周期与异步生成）；
 * {@link mchorse.bbs_ai.core.ConfigSyncManager} 加密配置读写（AES-GCM，UUID 派生密钥，
 * 与外部 Python 工具链共享）；{@link mchorse.bbs_ai.core.BBSAISettings} 全部设置项注册
 * （经 SettingsBuilder【原版兼容】）；{@link mchorse.bbs_ai.core.AIGenerationSink} 等跨模块契约。</p>
 */
package mchorse.bbs_ai.core;
