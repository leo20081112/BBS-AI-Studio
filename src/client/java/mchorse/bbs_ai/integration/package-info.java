/**
 * 客户端底层集成
 *
 * <p>{@link mchorse.bbs_ai.integration.BBSAIClientIntegration} 由
 * BBSModClient.onInitializeClient 末尾直接调用（原独立 client 入口点与
 * bbs-client-addon 入口已并入底层）：UUID 注入、modes 标签、MC 键绑定、
 * 服务初始化、本地组件检测与调试桥【原版兼容】。</p>
 */
package mchorse.bbs_ai.integration;
