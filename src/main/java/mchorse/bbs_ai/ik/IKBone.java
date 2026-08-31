package mchorse.bbs_ai.ik;

import org.joml.Vector3f;

/**
 * IK 骨骼数据
 *
 * <p>描述骨骼链中的单根骨骼：头 / 尾位置、静态长度、当前旋转与
 * 每轴独立旋转限制（对应 Blender 骨骼的 IK 旋转限制）。</p>
 *
 * <p>坐标均为世界空间；旋转为欧拉角（度），应用于骨骼自身轴。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class IKBone
{
    /**
     * 骨骼名称（对应 Form 骨骼名）
     */
    public final String name;

    /**
     * 骨骼头（起点）世界位置
     */
    public final Vector3f head = new Vector3f();

    /**
     * 骨骼尾（终点）世界位置
     */
    public final Vector3f tail = new Vector3f();

    /**
     * 静态长度（rest length，构建时由头尾距离确定）
     */
    public float restLength = 1.0F;

    /**
     * 当前旋转增量（欧拉，度，解算器写入）
     */
    public final Vector3f rotation = new Vector3f();

    /**
     * 解算前的原始旋转（度，FK 状态，Influence 混合用）
     */
    public final Vector3f restRotation = new Vector3f();

    /**
     * 是否启用旋转限制
     */
    public boolean useLimits = false;

    /**
     * X 轴旋转限制 [min, max]（度）
     */
    public final Vector3f limitMin = new Vector3f(-180.0F, -180.0F, -180.0F);

    /**
     * X 轴旋转限制 [min, max]（度）
     */
    public final Vector3f limitMax = new Vector3f(180.0F, 180.0F, 180.0F);

    /**
     * 该骨骼在本次解算中的影响权重（配合 Weight Falloff）
     */
    public float weight = 1.0F;

    public IKBone(String name)
    {
        this.name = name;
    }

    /**
     * 由头尾位置确定静态长度
     */
    public void updateRestLength()
    {
        this.restLength = this.head.distance(this.tail);
    }

    /**
     * 设置头尾位置（保持静态长度由调用方保证）
     */
    public void setPositions(Vector3f head, Vector3f tail)
    {
        this.head.set(head);
        this.tail.set(tail);

        this.updateRestLength();
    }

    /**
     * 应用旋转限制（逐轴 clamp）
     */
    public void applyLimits()
    {
        if (!this.useLimits)
        {
            return;
        }

        this.rotation.x = clamp(this.rotation.x, this.limitMin.x, this.limitMax.x);
        this.rotation.y = clamp(this.rotation.y, this.limitMin.y, this.limitMax.y);
        this.rotation.z = clamp(this.rotation.z, this.limitMin.z, this.limitMax.z);
    }

    /**
     * 复位到 FK 原始状态
     */
    public void resetToRest()
    {
        this.rotation.set(this.restRotation);
    }

    /**
     * 数值限幅
     */
    private static float clamp(float value, float min, float max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
