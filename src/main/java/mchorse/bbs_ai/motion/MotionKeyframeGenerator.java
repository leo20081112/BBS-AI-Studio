package mchorse.bbs_ai.motion;

import mchorse.bbs_ai.BBSAIStudio;
import mchorse.bbs_ai.format.MotionData;
import mchorse.bbs_ai.format.MotionFrame;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 动作关键帧生成器
 *
 * <p>将姿态估计得到的逐帧关键点序列转换为统一导出格式的 {@link MotionData}：
 * <ol>
 *   <li>逐帧骨骼映射（{@link SkeletonMapper}）</li>
 *   <li>OneEuro 自适应低通平滑（逐骨骼、逐欧拉分量）</li>
 *   <li>置信度门控：不可信关节保持上一帧取值，避免抖动尖刺</li>
 *   <li>简化 Foot Lock：检测踝部竖直静止时冻结腿部摆动</li>
 *   <li>时间刻度换算：tick = 源帧序号 × (20 / 源帧率)</li>
 *   <li>输出写入 {@code config/bbs/ai_cache/}</li>
 * </ol></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class MotionKeyframeGenerator
{
    /**
     * OneEuro 滤波器通道
     */
    private static class OneEuroChannel
    {
        private float minCutoff;
        private float beta;

        private Float previousValue;
        private Float previousDerivative;

        OneEuroChannel(float minCutoff, float beta)
        {
            this.minCutoff = Math.max(0.01F, minCutoff);
            this.beta = beta;
        }

        /**
         * 滤波一个采样值
         */
        float filter(float value, float dt)
        {
            float derivative = this.previousValue == null ? 0.0F : (value - this.previousValue) / Math.max(dt, 0.0001F);

            /* 导数低通 */
            float dCutoff = 1.0F;
            float alphaD = alpha(dCutoff, dt);
            float filteredDerivative = this.previousDerivative == null ? derivative : alphaD * derivative + (1.0F - alphaD) * this.previousDerivative;

            /* 自适应截止频率：随速度增大而增大（减少高速段延迟） */
            float cutoff = this.minCutoff + this.beta * Math.abs(filteredDerivative);
            float alpha = alpha(cutoff, dt);
            float filtered = this.previousValue == null ? value : alpha * value + (1.0F - alpha) * this.previousValue;

            this.previousValue = filtered;
            this.previousDerivative = filteredDerivative;

            return filtered;
        }

        /**
         * 指数平滑系数
         */
        private static float alpha(float cutoff, float dt)
        {
            float tau = 1.0F / (2.0F * (float) Math.PI * cutoff);

            return 1.0F / (1.0F + tau / dt);
        }
    }

    /**
     * 生成动作数据（阻塞式，需在后台线程调用）
     *
     * @param keypointsList 逐帧关键点（已按时间排序）
     * @param sourceFps     源视频帧率
     * @param sampleEvery   抽帧间隔（提取时每隔 N 帧取 1 帧）
     * @param minConfidence 最低关键点置信度
     * @param smoothing     平滑强度（0 = 不平滑，1 = 最强）
     * @param footLock      是否启用脚部锁定
     * @param mirror        是否镜像（左右互换）
     * @param modelName     元信息用模型名称
     * @param videoName     元信息用源文件名
     * @param progress      进度回调（0.0 ~ 1.0），可为 null
     * @return 动作数据（tick 已换算，帧已平滑）
     */
    public static MotionData generate(List<PoseKeypoints> keypointsList, float sourceFps, int sampleEvery,
        float minConfidence, float smoothing, boolean footLock, boolean mirror,
        String modelName, String videoName, Consumer<Float> progress)
    {
        MotionData data = new MotionData();
        SkeletonMapper mapper = new SkeletonMapper(mirror);

        /* 平滑强度 → OneEuro 参数：强度越大截止频率越低 */
        float clamped = Math.max(0.0F, Math.min(1.0F, smoothing));
        float minCutoff = 3.0F * (1.0F - clamped) + 0.3F;
        float beta = 0.5F + 3.0F * clamped;

        float dt = Math.max(1.0F / (sourceFps / Math.max(1, sampleEvery)), 1.0F / 60.0F);

        /* 逐骨骼逐分量的滤波通道 */
        Map<String, OneEuroChannel[]> channels = new HashMap<>();

        for (String bone : SkeletonMapper.ALL_BONES)
        {
            channels.put(bone, new OneEuroChannel[] {
                new OneEuroChannel(minCutoff, beta),
                new OneEuroChannel(minCutoff, beta),
                new OneEuroChannel(minCutoff, beta)
            });
        }

        /* 上一次有效骨骼姿态（置信度门控保持用） */
        Map<String, float[]> previous = new HashMap<>();

        /* Foot Lock 状态：上一帧踝部竖直位置 */
        Float previousLeftAnkleY = null;
        Float previousRightAnkleY = null;
        boolean leftLocked = false;
        boolean rightLocked = false;

        List<MotionFrame> frames = new ArrayList<>(keypointsList.size());

        for (int i = 0; i < keypointsList.size(); i++)
        {
            PoseKeypoints keypoints = keypointsList.get(i);
            Map<String, float[]> mapped = mapper.map(keypoints, minConfidence);
            MotionFrame frame = new MotionFrame(sourceTick(i, sampleEvery, sourceFps));

            /* Foot Lock 检测：踝部竖直位移极小视为触地，冻结该侧腿部摆动 */
            if (footLock && keypoints.isValid(PoseKeypoints.LEFT_ANKLE, minConfidence))
            {
                leftLocked = previousLeftAnkleY != null && Math.abs(keypoints.y[PoseKeypoints.LEFT_ANKLE] - previousLeftAnkleY) < 0.004F;
                previousLeftAnkleY = keypoints.y[PoseKeypoints.LEFT_ANKLE];
            }

            if (footLock && keypoints.isValid(PoseKeypoints.RIGHT_ANKLE, minConfidence))
            {
                rightLocked = previousRightAnkleY != null && Math.abs(keypoints.y[PoseKeypoints.RIGHT_ANKLE] - previousRightAnkleY) < 0.004F;
                previousRightAnkleY = keypoints.y[PoseKeypoints.RIGHT_ANKLE];
            }

            for (String bone : SkeletonMapper.ALL_BONES)
            {
                float[] rotation = mapped.get(bone);

                /* 置信度门控：本帧不可信时保持上一帧取值 */
                if (rotation == null)
                {
                    rotation = previous.get(bone);

                    if (rotation == null)
                    {
                        continue;
                    }
                }

                /* Foot Lock：锁定帧时保持上一帧腿部旋转 */
                if (footLock && bone.equals(SkeletonMapper.LEFT_LEG) && leftLocked && previous.containsKey(bone))
                {
                    rotation = previous.get(bone);
                }

                if (footLock && bone.equals(SkeletonMapper.RIGHT_LEG) && rightLocked && previous.containsKey(bone))
                {
                    rotation = previous.get(bone);
                }

                float[] filtered = new float[3];
                OneEuroChannel[] boneChannels = channels.get(bone);

                for (int axis = 0; axis < 3; axis++)
                {
                    filtered[axis] = boneChannels[axis].filter(rotation[axis], dt);
                }

                previous.put(bone, filtered);
                frame.bone(bone, filtered[0], filtered[1], filtered[2]);
            }

            if (!frame.bones.isEmpty())
            {
                frames.add(frame);
            }

            if (progress != null && i % 10 == 0)
            {
                progress.accept((i + 1) / (float) keypointsList.size());
            }
        }

        data.keyframes.addAll(frames);
        data.stampGenerated("video", videoName, Math.round(sourceFps), keypointsList.size(), modelName, "bbs_ai_studio_ingame");

        return data;
    }

    /**
     * 换算时间刻度：采样帧序号 → Minecraft tick（20 ticks = 1 秒）
     */
    private static int sourceTick(int sampleIndex, int sampleEvery, float sourceFps)
    {
        return Math.round(sampleIndex * sampleEvery * 20.0F / Math.max(1.0F, sourceFps));
    }

    /**
     * 生成并写入缓存目录
     *
     * @return 写入的文件
     */
    public static File generateAndSave(List<PoseKeypoints> keypointsList, float sourceFps, int sampleEvery,
        float minConfidence, float smoothing, boolean footLock, boolean mirror,
        String modelName, String videoName, Consumer<Float> progress)
    {
        MotionData data = generate(keypointsList, sourceFps, sampleEvery, minConfidence, smoothing, footLock, mirror, modelName, videoName, progress);

        String baseName = videoName == null || videoName.isEmpty() ? "motion" : videoName;
        int dot = baseName.lastIndexOf('.');

        if (dot > 0)
        {
            baseName = baseName.substring(0, dot);
        }

        File output = new File(BBSAIStudio.getAICacheFolder(), baseName + "_" + System.currentTimeMillis() / 1000 + ".json");

        data.writeToFile(output);

        return output;
    }
}
