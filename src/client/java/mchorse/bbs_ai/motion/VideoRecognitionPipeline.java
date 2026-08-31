package mchorse.bbs_ai.motion;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.imageio.ImageIO;

import mchorse.bbs_ai.BBSAIStudio;
import mchorse.bbs_ai.core.BBSAISettings;
import mchorse.bbs_ai.format.MotionData;
import mchorse.bbs_ai.integration.event.AIGenerationCompleteEvent;
import mchorse.bbs_ai.motion.LocalPoseEstimator.PoseModel;
import mchorse.bbs_ai.preview.PreviewContext;
import mchorse.bbs_ai.preview.PreviewSystem;
import mchorse.bbs_ai.preview.PreviewTrack;
import mchorse.bbs_mod.BBSMod;

/**
 * 游戏内视频 → 动作数据 识别流水线（客户端编排）
 *
 * <p>串联模块 2 的四个阶段（全部在后台线程执行）：
 * <ol>
 *   <li>{@link VideoFrameExtractor#extract} —— FFmpeg 抽帧</li>
 *   <li>{@link LocalPoseEstimator#estimate} —— ONNX 姿态估计（逐帧）</li>
 *   <li>{@link MotionKeyframeGenerator#generate} —— 平滑 + 骨骼映射 + 关键帧</li>
 *   <li>{@link PreviewSystem#stage} —— 进入预烘焙预览 + 发布 AIGenerationCompleteEvent</li>
 * </ol>
 * 结果同时写入 {@code config/bbs/ai_cache/} 供统一导入管理器发现。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class VideoRecognitionPipeline
{
    /**
     * 运行状态
     */
    private volatile boolean running;

    /**
     * 是否正在运行
     */
    public boolean isRunning()
    {
        return this.running;
    }

    /**
     * 异步执行识别流水线（重复调用被忽略）
     *
     * @param video       输入视频
     * @param sampleEvery 抽帧间隔（每 N 帧取 1 帧）
     * @param mirror      是否镜像
     * @param filmId      目标影片 ID（可为空）
     * @param replayId    目标角色 ID（可为空）
     * @param onProgress  进度回调（主线程外，0.0~1.0）
     * @param onDone      完成回调（后台线程；参数为输出文件或 null=失败）
     * @param onError     失败回调（后台线程，参数为错误消息）
     */
    public synchronized void start(File video, int sampleEvery, boolean mirror, String filmId, String replayId,
        Consumer<Float> onProgress, Consumer<File> onDone, Consumer<String> onError)
    {
        if (this.running)
        {
            if (onError != null)
            {
                onError.accept("已有识别任务在运行中");
            }

            return;
        }

        this.running = true;

        Thread thread = new Thread(() ->
        {
            File output = null;

            try
            {
                /* 阶段 0~1：抽帧（进度 0~30%） */
                File framesFolder = VideoFrameExtractor.getFramesFolder(String.valueOf(System.currentTimeMillis()));
                VideoFrameExtractor.ExtractionResult extraction = VideoFrameExtractor.extract(video, framesFolder, sampleEvery,
                    (p) ->
                    {
                        if (onProgress != null)
                        {
                            onProgress.accept(p * 0.3F);
                        }
                    });

                /* 阶段 2：姿态估计（进度 30~85%） */
                PoseModel model = BBSAISettings.motionPoseModel.get() == 1 ? PoseModel.RTMPOSE : PoseModel.YOLOV8;
                String modelName = model == PoseModel.RTMPOSE ? "rtmpose" : "yolov8";
                float minConfidence = BBSAISettings.motionMinConfidence.get();

                List<mchorse.bbs_ai.motion.PoseKeypoints> keypoints = new ArrayList<>();

                try (LocalPoseEstimator estimator = new LocalPoseEstimator(model))
                {
                    for (int i = 0; i < extraction.frames.size(); i++)
                    {
                        BufferedImage frame = ImageIO.read(extraction.frames.get(i));

                        if (frame == null)
                        {
                            continue;
                        }

                        keypoints.add(estimator.estimate(frame, i, minConfidence));

                        if (i % 5 == 0 && onProgress != null)
                        {
                            onProgress.accept(0.3F + (i + 1) / (float) extraction.frames.size() * 0.55F);
                        }
                    }
                }

                if (keypoints.isEmpty())
                {
                    throw new IOException("没有检测到任何人体姿态，请检查视频内容");
                }

                /* 阶段 3：关键帧生成（进度 85~100%） */
                MotionData data = MotionKeyframeGenerator.generate(keypoints, extraction.sourceFps, sampleEvery,
                    minConfidence,
                    BBSAISettings.motionSmoothing.get(),
                    BBSAISettings.motionFootLock.get(),
                    mirror,
                    modelName,
                    video.getName(),
                    (p) ->
                    {
                        if (onProgress != null)
                        {
                            onProgress.accept(0.85F + p * 0.15F);
                        }
                    });

                /* 写入缓存目录 */
                String baseName = video.getName();
                int dot = baseName.lastIndexOf('.');

                if (dot > 0)
                {
                    baseName = baseName.substring(0, dot);
                }

                output = new File(BBSAIStudio.getAICacheFolder(), baseName + "_" + System.currentTimeMillis() / 1000 + ".json");
                data.writeToFile(output);

                /* 进入预览系统 */
                List<mchorse.bbs_ai.format.MotionFrame> frames = data.keyframes;

                if (!frames.isEmpty())
                {
                    PreviewTrack track = PreviewSystem.get().stage(filmId, replayId, "", mchorse.bbs_ai.import_manager.SourceType.INTERNAL_AI, frames);
                    PreviewContext context = PreviewSystem.get().getCache().getContext(track == null ? "" : filmId + "|" + (replayId == null || replayId.isEmpty() ? "0" : replayId) + "|");

                    /* 发布生成完成事件（供 addon 扩展） */
                    BBSMod.events.post(new AIGenerationCompleteEvent(context, video.getName()));
                }
            }
            catch (Exception e)
            {
                System.err.println("[BBS AI] 视频识别失败：" + e.getMessage());
                e.printStackTrace();

                if (onError != null)
                {
                    onError.accept(e.getMessage() == null ? e.toString() : e.getMessage());
                }

                this.running = false;

                return;
            }

            this.running = false;

            if (onDone != null)
            {
                onDone.accept(output);
            }
        }, "BBS-AI-Recognize");

        thread.setDaemon(true);
        thread.start();
    }
}
