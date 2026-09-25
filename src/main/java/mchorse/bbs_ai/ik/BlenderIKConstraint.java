package mchorse.bbs_ai.ik;

import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Blender IK 约束数据（完全复刻 Blender IK 约束的全部参数）
 *
 * <p>参数与 Blender 一一对应：
 * <ul>
 *   <li><b>Target</b>：目标位置，末端骨骼始终指向这里</li>
 *   <li><b>Pole Target</b>：极向目标，控制中间关节弯曲方向</li>
 *   <li><b>Pole Angle</b>：极向角度偏移（-180 ~ 180）</li>
 *   <li><b>Chain Length</b>：从末端向上影响多少级骨骼（0 = 全部）</li>
 *   <li><b>Use Tail</b>：是否使用末端骨骼尾点对齐目标</li>
 *   <li><b>Follow（锚点跟随）</b>：核心参数——目标移动超出可达范围时根部是否跟随</li>
 *   <li><b>Influence</b>：FK/IK 混合权重（0.0 ~ 1.0）</li>
 *   <li><b>Rotation Limits</b>：每根骨骼的 X/Y/Z 独立旋转限制</li>
 *   <li><b>Stretch</b> + <b>Stretch Limit</b>：拉伸及上限比例（1.0 ~ 2.0）</li>
 *   <li><b>Target Rotation</b>：末端骨骼复制目标旋转</li>
 *   <li><b>Weight Falloff</b>：越靠近根部影响越小的衰减</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BlenderIKConstraint
{
    /**
     * 目标位置（Target Empty）
     *
     * <p>语义：当 {@link #worldOrigin} 非 null 时为<b>世界绝对坐标</b>
     * （Gizmo 投影锚定用），解算时取 {@link #getTargetLocal()}；
     * worldOrigin 为 null（默认，纯数据用法）时即链局部坐标。</p>
     */
    public final Vector3f target = new Vector3f();

    /**
     * 链根的世界锚点（null = target 即局部坐标）。
     * Gizmo 世界锚定模式下由宿主设置（如玩家脚下位置），
     * 拖拽 Gizmo 改动的是世界 target，解算器使用相对此锚点的局部目标。
     */
    public Vector3f worldOrigin;

    /**
     * 解算用局部目标：worldOrigin 非 null 时 = target - worldOrigin，否则 = target
     */
    public Vector3f getTargetLocal()
    {
        if (this.worldOrigin == null)
        {
            return this.target;
        }

        return new Vector3f(this.target).sub(this.worldOrigin);
    }

    /**
     * 极向目标位置（Pole Target Empty，可为 null = 无极向）
     */
    public Vector3f poleTarget;

    /**
     * 极向角度偏移（度，-180 ~ 180）
     */
    public float poleAngle = 0.0F;

    /**
     * 链长度（从末端向上受影响的骨骼级数；0 = 整条链）
     */
    public int chainLength = 0;

    /**
     * 使用末端（Use Tail）：末端尾点对齐目标；false 时头点对齐
     */
    public boolean useTail = true;

    /**
     * 锚点跟随（Follow）：目标超出 reach 时根骨骼跟随移动
     */
    public boolean useAnchor = false;

    /**
     * FK/IK 混合影响（0.0 ~ 1.0）
     */
    public float influence = 1.0F;

    /**
     * 使用拉伸（Stretch）
     */
    public boolean useStretch = false;

    /**
     * 拉伸上限比例（1.0 ~ 2.0）
     */
    public float stretchLimit = 1.15F;

    /**
     * 使用目标旋转：末端骨骼复制目标旋转
     */
    public boolean useTargetRotation = false;

    /**
     * 目标旋转（欧拉，度；useTargetRotation = true 时生效）
     */
    public final Vector3f targetRotation = new Vector3f();

    /**
     * 权重衰减开关
     */
    public boolean useWeightFalloff = false;

    /**
     * 权重衰减强度（0 = 不衰减，1 = 根部权重趋近 0）
     */
    public float weightFalloff = 0.5F;

    /* ====================================================================
     * 锚点跟随（手/脚旋转时的锚点联动）
     * ==================================================================== */

    /**
     * 锚定模式：0 = 无，1 = 脚部贴地（末端钉回锚定位置，防滑），2 = 手部抓附（锚点随末端旋转联动，目标跟转）
     */
    public int anchorMode = 0;

    /**
     * 锚定强度（0~1，补偿权重）
     */
    public float anchorStrength = 1.0F;

    /**
     * 释放阈值（度）：末端相对锚定时刻的旋转偏移超过该值后释放锚定
     * （脚抬步 / 手松开），随后在新的位置重新锚定
     */
    public float anchorReleaseAngle = 25.0F;

    /**
     * 锚定激活时的末端端点快照（运行时状态；FOOT 模式即贴地点）
     */
    public final Vector3f anchorPoint = new Vector3f();

    /**
     * 锚定激活时的末端旋转快照（欧拉，度，运行时状态）
     */
    public final Vector3f anchorRestRotation = new Vector3f();

    /**
     * 锚定是否处于激活状态（运行时状态，由解算器维护）
     */
    public boolean anchored;

    /**
     * 每根骨骼的旋转限制（骨骼名 → [是否启用, min(x,y,z), max(x,y,z)]）
     */
    public final Map<String, BoneLimit> rotationLimits = new LinkedHashMap<>();

    /**
     * 单根骨骼的旋转限制
     */
    public static class BoneLimit
    {
        /**
         * 是否启用限制
         */
        public boolean enabled = true;

        /**
         * 最小旋转（度，x/y/z）
         */
        public final Vector3f min = new Vector3f(-180.0F, -180.0F, -180.0F);

        /**
         * 最大旋转（度，x/y/z）
         */
        public final Vector3f max = new Vector3f(180.0F, 180.0F, 180.0F);

        public BoneLimit()
        {}

        public BoneLimit(Vector3f min, Vector3f max)
        {
            this.min.set(min);
            this.max.set(max);
        }
    }

    /**
     * 设置某骨骼的旋转限制
     */
    public BlenderIKConstraint setLimit(String boneName, boolean enabled, Vector3f min, Vector3f max)
    {
        BoneLimit limit = new BoneLimit(min, max);

        limit.enabled = enabled;
        this.rotationLimits.put(boneName, limit);

        return this;
    }

    /**
     * 获取某骨骼的旋转限制（无则返回 null）
     */
    public BoneLimit getLimit(String boneName)
    {
        return this.rotationLimits.get(boneName);
    }

    /**
     * 计算某骨骼在链中的权重（Weight Falloff：越靠近根部权重越小）
     *
     * @param index        骨骼在链中的下标（0 = 末端）
     * @param affectedSize 本次受影响的骨骼数
     */
    public float weightFor(int index, int affectedSize)
    {
        if (!this.useWeightFalloff || affectedSize <= 1)
        {
            return 1.0F;
        }

        /* 线性衰减：末端 1.0，根部最低 (1 - weightFalloff) */
        float t = index / (float) (affectedSize - 1);

        return 1.0F - t * Math.max(0.0F, Math.min(1.0F, this.weightFalloff));
    }

    /**
     * 构建链的可达距离（reach）：全部长度之和 × 拉伸上限
     */
    public float calculateReach(IKBoneChain chain)
    {
        return chain.calculateRestLength() * Math.max(1.0F, this.stretchLimit);
    }
}
