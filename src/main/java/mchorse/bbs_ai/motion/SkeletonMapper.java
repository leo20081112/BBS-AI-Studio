package mchorse.bbs_ai.motion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 骨骼映射器：COCO 17 关键点 → BBS 标准 6 骨骼旋转
 *
 * <p>映射规则（源自项目规范）：
 * <ul>
 *   <li>{@code head}：鼻子 + 双眼 + 双耳中心</li>
 *   <li>{@code body}：双肩 + 双髋中心</li>
 *   <li>{@code left_arm}：左肩 → 左肘 → 左腕</li>
 *   <li>{@code right_arm}：右肩 → 右肘 → 右腕</li>
 *   <li>{@code left_leg}：左髋 → 左膝 → 左踝</li>
 *   <li>{@code right_leg}：右髋 → 右膝 → 右踝</li>
 * </ul></p>
 *
 * <p>旋转估算：以 2D 关键点几何计算各肢体的俯仰（前后摆动，绕 X 轴）与
 * 侧向偏摆（绕 Z 轴），头部额外根据鼻眼几何估算偏航（绕 Y 轴）。
 * 输出为欧拉角（度），符合统一导出格式约定。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class SkeletonMapper
{
    /**
     * BBS 标准骨骼名称
     */
    public static final String HEAD = "head";
    public static final String BODY = "body";
    public static final String LEFT_ARM = "left_arm";
    public static final String RIGHT_ARM = "right_arm";
    public static final String LEFT_LEG = "left_leg";
    public static final String RIGHT_LEG = "right_leg";

    /**
     * 全部标准骨骼
     */
    public static final String[] ALL_BONES = {HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG};

    /**
     * 是否镜像输入（前置摄像头拍摄的视频通常是镜像的）
     */
    private boolean mirror;

    public SkeletonMapper(boolean mirror)
    {
        this.mirror = mirror;
    }

    /**
     * 将单帧关键点映射为骨骼旋转（单位：度）
     *
     * @param keypoints     关键点
     * @param minConfidence 最低置信度
     * @return 骨骼名 → [x, y, z]；完全无法检测的骨骼不出现在结果中
     */
    public Map<String, float[]> map(PoseKeypoints keypoints, float minConfidence)
    {
        Map<String, float[]> result = new LinkedHashMap<>();

        if (keypoints == null)
        {
            return result;
        }

        /* 中间几何点 */
        float[] neck = new float[2];
        float[] hipCenter = new float[2];
        boolean hasNeck = keypoints.midPoint(PoseKeypoints.LEFT_SHOULDER, PoseKeypoints.RIGHT_SHOULDER, minConfidence, neck);
        boolean hasHip = keypoints.midPoint(PoseKeypoints.LEFT_HIP, PoseKeypoints.RIGHT_HIP, minConfidence, hipCenter);

        /* ---- 躯干：肩线倾斜 → roll，髋肩线偏移 → lean（绕 X） ---- */
        if (hasNeck && hasHip)
        {
            float lean = angleOf(neck[0], neck[1], hipCenter[0], hipCenter[1]);
            float roll = 0.0F;

            if (keypoints.isValid(PoseKeypoints.LEFT_SHOULDER, minConfidence) && keypoints.isValid(PoseKeypoints.RIGHT_SHOULDER, minConfidence))
            {
                roll = angleBetweenHorizontal(
                    keypoints.x[PoseKeypoints.LEFT_SHOULDER], keypoints.y[PoseKeypoints.LEFT_SHOULDER],
                    keypoints.x[PoseKeypoints.RIGHT_SHOULDER], keypoints.y[PoseKeypoints.RIGHT_SHOULDER]
                );

                roll = clampRoll(roll);
            }

            /* 躯干前倾：与竖直方向的夹角 */
            float bodyLean = (float) Math.toDegrees(lean);

            /* 视频中人物面向 −Z，前倾角转换为绕 X 旋转 */
            result.put(BODY, new float[] {bodyLean * directionFactor(keypoints), 0.0F, roll});
        }

        /* ---- 头部：鼻/眼中心相对颈部 → pitch，双耳可见性 → yaw ---- */
        if (hasNeck && keypoints.isValid(PoseKeypoints.NOSE, minConfidence))
        {
            float headPitch = angleOf(keypoints.x[PoseKeypoints.NOSE], keypoints.y[PoseKeypoints.NOSE], neck[0], neck[1]);
            float yaw = 0.0F;

            /* 用左右眼/耳 x 差估算头部偏航 */
            if (keypoints.isValid(PoseKeypoints.LEFT_EYE, minConfidence) && keypoints.isValid(PoseKeypoints.RIGHT_EYE, minConfidence))
            {
                float eyeSpread = Math.abs(keypoints.x[PoseKeypoints.LEFT_EYE] - keypoints.x[PoseKeypoints.RIGHT_EYE]);
                float eyeCenterX = (keypoints.x[PoseKeypoints.LEFT_EYE] + keypoints.x[PoseKeypoints.RIGHT_EYE]) / 2.0F;

                /* 眼距相对头宽（耳距）的收缩比例 → 偏航角 */
                float headWidth = headWidthEstimate(keypoints, minConfidence);

                if (headWidth > 0.001F)
                {
                    float ratio = Math.max(0.0F, Math.min(1.0F, eyeSpread / headWidth));
                    float sign = eyeCenterX < neck[0] ? 1.0F : -1.0F;

                    yaw = sign * (float) Math.toDegrees(Math.acos(Math.max(0.0F, Math.min(1.0F, ratio))));
                }
            }

            result.put(HEAD, new float[] {(float) Math.toDegrees(headPitch) * directionFactor(keypoints) - 10.0F, yaw, 0.0F});
        }

        /* ---- 四肢：上段与下段平均摆角 ---- */
        this.mapLimb(keypoints, minConfidence, result,
            PoseKeypoints.LEFT_SHOULDER, PoseKeypoints.LEFT_ELBOW, PoseKeypoints.LEFT_WRIST, LEFT_ARM);
        this.mapLimb(keypoints, minConfidence, result,
            PoseKeypoints.RIGHT_SHOULDER, PoseKeypoints.RIGHT_ELBOW, PoseKeypoints.RIGHT_WRIST, RIGHT_ARM);
        this.mapLimb(keypoints, minConfidence, result,
            PoseKeypoints.LEFT_HIP, PoseKeypoints.LEFT_KNEE, PoseKeypoints.LEFT_ANKLE, LEFT_LEG);
        this.mapLimb(keypoints, minConfidence, result,
            PoseKeypoints.RIGHT_HIP, PoseKeypoints.RIGHT_KNEE, PoseKeypoints.RIGHT_ANKLE, RIGHT_LEG);

        return result;
    }

    /**
     * 映射单条肢体（肩/髋 → 肘/膝 → 腕/踝）
     */
    private void mapLimb(PoseKeypoints keypoints, float minConfidence, Map<String, float[]> result, int root, int mid, int end, String boneName)
    {
        if (!keypoints.isValid(root, minConfidence) || !keypoints.isValid(mid, minConfidence))
        {
            return;
        }

        /* 上段摆角（相对竖直方向） */
        float upper = limbSwing(keypoints.x[root], keypoints.y[root], keypoints.x[mid], keypoints.y[mid]);

        /* 下段摆角（存在时与上段平均，模拟肢体自然弯曲） */
        float swing = upper;

        if (keypoints.isValid(end, minConfidence))
        {
            float lower = limbSwing(keypoints.x[mid], keypoints.y[mid], keypoints.x[end], keypoints.y[end]);

            swing = (upper + lower) / 2.0F;
        }

        /* 镜像修正：BBS 中 left_arm 对应画面右侧（人物自身左臂） */
        if (this.mirror)
        {
            swing = -swing;
        }

        /* 腿部与手臂绕 X 摆动为主，附带小幅 Z 展开角 */
        float zSpread = 0.0F;

        if (boneName.equals(LEFT_LEG) || boneName.equals(RIGHT_LEG))
        {
            zSpread = boneName.equals(LEFT_LEG) ? -2.0F : 2.0F;
        }

        result.put(boneName, new float[] {swing * 180.0F / (float) Math.PI, 0.0F, zSpread});
    }

    /**
     * 肢体摆角（弧度）：相对竖直向下方向的偏角，左右方向带符号
     */
    private float limbSwing(float x1, float y1, float x2, float y2)
    {
        float dx = x2 - x1;
        float dy = y2 - y1;

        /* 竖直向下为 0，向画面左/右摆动产生正负偏角 */
        return (float) Math.atan2(dx, Math.abs(dy) + 0.0001F);
    }

    /**
     * 两点连线相对竖直方向的夹角（弧度，无符号）
     */
    private float angleOf(float x1, float y1, float x2, float y2)
    {
        float dx = x2 - x1;
        float dy = y2 - y1;

        return (float) Math.atan2(Math.abs(dx), Math.abs(dy) + 0.0001F);
    }

    /**
     * 肩线（或髋线）相对水平面的倾角（度）
     */
    private float angleBetweenHorizontal(float x1, float y1, float x2, float y2)
    {
        return (float) Math.toDegrees(Math.atan2(y2 - y1, Math.abs(x2 - x1) + 0.0001F));
    }

    /**
     * 侧倾角限幅（±30 度），避免极端检测值破坏姿态
     */
    private float clampRoll(float roll)
    {
        return Math.max(-30.0F, Math.min(30.0F, roll));
    }

    /**
     * 头宽估计（耳距或眼距的 1.6 倍兜底）
     */
    private float headWidthEstimate(PoseKeypoints keypoints, float minConfidence)
    {
        if (keypoints.isValid(PoseKeypoints.LEFT_EAR, minConfidence) && keypoints.isValid(PoseKeypoints.RIGHT_EAR, minConfidence))
        {
            return Math.abs(keypoints.x[PoseKeypoints.LEFT_EAR] - keypoints.x[PoseKeypoints.RIGHT_EAR]);
        }

        if (keypoints.isValid(PoseKeypoints.LEFT_EYE, minConfidence) && keypoints.isValid(PoseKeypoints.RIGHT_EYE, minConfidence))
        {
            return Math.abs(keypoints.x[PoseKeypoints.LEFT_EYE] - keypoints.x[PoseKeypoints.RIGHT_EYE]) * 1.6F;
        }

        return 0.0F;
    }

    /**
     * 方向因子：根据髋部相对肩部的位置粗略判断人物朝向，
     * 修正前后倾角的符号（背面视角与正面视角相反）
     */
    private float directionFactor(PoseKeypoints keypoints)
    {
        return this.mirror ? -1.0F : 1.0F;
    }

    /**
     * 列出全部骨骼名
     */
    public static List<String> boneNames()
    {
        return new ArrayList<>(java.util.Arrays.asList(ALL_BONES));
    }
}
