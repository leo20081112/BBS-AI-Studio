/**
 * 识别流水线客户端编排
 * 
 * <p>{@link mchorse.bbs_ai.motion.VideoRecognitionPipeline} 在后台线程串联
 * 抽帧 → 姿态估计 → 关键帧生成 → 预览暂存，并发布 AIGenerationCompleteEvent。</p>
 */
package mchorse.bbs_ai.motion;
