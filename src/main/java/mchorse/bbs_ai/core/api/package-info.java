/**
 * AI 厂商 API 抽象层
 * 
 * <p>{@link mchorse.bbs_ai.core.api.AIProvider} 统一接口（同步 generate + 异步 generateAsync）；
 * {@link mchorse.bbs_ai.core.api.InGameAPIProvider} OkHttp 实现，支持 OpenAI 兼容协议与
 * Anthropic 协议、视觉输入与完整错误映射；{@link mchorse.bbs_ai.core.api.APIResponseParser}
 * 响应解析与模型输出清洗。全部网络 IO 限定在后台线程。</p>
 */
package mchorse.bbs_ai.core.api;
