/**
 * 视频 → 动作识别流水线
 * 
 * <p>{@link mchorse.bbs_ai.motion.VideoFrameExtractor}（FFmpeg 外部进程抽帧）、
 * {@link mchorse.bbs_ai.motion.LocalPoseEstimator}（ONNX Runtime 推理，YOLOv8-pose / RTMPose）、
 * {@link mchorse.bbs_ai.motion.SkeletonMapper}（COCO-17 → BBS 6 骨骼）、
 * {@link mchorse.bbs_ai.motion.MotionKeyframeGenerator}（OneEuro 平滑 + Foot Lock + tick 换算）。
 * 客户端编排见 client 源集同名包。</p>
 */
package mchorse.bbs_ai.motion;
