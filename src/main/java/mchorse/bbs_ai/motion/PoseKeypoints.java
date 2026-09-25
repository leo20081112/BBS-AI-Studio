package mchorse.bbs_ai.motion;

import java.util.Arrays;

/**
 * 姿态关键点中间表示（COCO 17 点）
 *
 * <p>关键点索引遵循 COCO 规范：
 * <pre>
 * 0 鼻子, 1 左眼, 2 右眼, 3 左耳, 4 右耳,
 * 5 左肩, 6 右肩, 7 左肘, 8 右肘, 9 左腕, 10 右腕,
 * 11 左髋, 12 右髋, 13 左膝, 14 右膝, 15 左踝, 16 右踝
 * </pre></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class PoseKeypoints
{
    /**
     * COCO 关键点数量
     */
    public static final int KEYPOINT_COUNT = 17;

    /**
     * 关键点名称索引常量
     */
    public static final int NOSE = 0;
    public static final int LEFT_EYE = 1;
    public static final int RIGHT_EYE = 2;
    public static final int LEFT_EAR = 3;
    public static final int RIGHT_EAR = 4;
    public static final int LEFT_SHOULDER = 5;
    public static final int RIGHT_SHOULDER = 6;
    public static final int LEFT_ELBOW = 7;
    public static final int RIGHT_ELBOW = 8;
    public static final int LEFT_WRIST = 9;
    public static final int RIGHT_WRIST = 10;
    public static final int LEFT_HIP = 11;
    public static final int RIGHT_HIP = 12;
    public static final int LEFT_KNEE = 13;
    public static final int RIGHT_KNEE = 14;
    public static final int LEFT_ANKLE = 15;
    public static final int RIGHT_ANKLE = 16;

    /**
     * 关键点 x 坐标（归一化 0~1，相对视频帧宽度）
     */
    public final float[] x = new float[KEYPOINT_COUNT];

    /**
     * 关键点 y 坐标（归一化 0~1，相对视频帧高度）
     */
    public final float[] y = new float[KEYPOINT_COUNT];

    /**
     * 关键点置信度（0~1）
     */
    public final float[] confidence = new float[KEYPOINT_COUNT];

    /**
     * 该帧整体检测置信度（通常取人体框置信度或关键点均值）
     */
    public float frameConfidence;

    /**
     * 对应的源视频帧序号
     */
    public int sourceFrame;

    public PoseKeypoints(int sourceFrame)
    {
        this.sourceFrame = sourceFrame;

        Arrays.fill(this.confidence, 0.0F);
    }

    /**
     * 设置单个关键点
     */
    public void setPoint(int index, float x, float y, float confidence)
    {
        if (index < 0 || index >= KEYPOINT_COUNT)
        {
            return;
        }

        this.x[index] = x;
        this.y[index] = y;
        this.confidence[index] = confidence;
    }

    /**
     * 指定关键点是否可信
     */
    public boolean isValid(int index, float minConfidence)
    {
        return index >= 0 && index < KEYPOINT_COUNT && this.confidence[index] >= minConfidence;
    }

    /**
     * 两点中点（任一点不可信时返回 false）
     */
    public boolean midPoint(int a, int b, float minConfidence, float[] out)
    {
        if (!this.isValid(a, minConfidence) || !this.isValid(b, minConfidence))
        {
            return false;
        }

        out[0] = (this.x[a] + this.x[b]) / 2.0F;
        out[1] = (this.y[a] + this.y[b]) / 2.0F;

        return true;
    }

    /**
     * 计算整体帧置信度（全部关键点均值）
     */
    public float averageConfidence()
    {
        float sum = 0.0F;

        for (float value : this.confidence)
        {
            sum += value;
        }

        return sum / KEYPOINT_COUNT;
    }
}
