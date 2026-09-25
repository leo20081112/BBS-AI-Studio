package mchorse.bbs_ai.ui.transform;

/**
 * 变换目标接口
 *
 * <p>Blender / Mine-imator 变换系统操作的目标抽象：把 G/R/S 变换
 * 落到具体数据（如当前选中角色在当前 tick 的位置 / 旋转）。
 * 由宿主面板实现并注入 {@code BlenderTransformSystem}。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public interface ITransformTarget
{
    /**
     * 是否存在可变换目标（如选中了角色 / 关键帧）
     */
    boolean hasTarget();

    /**
     * 获取当前位置 [x, y, z]
     */
    float[] getPosition();

    /**
     * 应用位移增量
     */
    void move(float dx, float dy, float dz);

    /**
     * 获取当前旋转 [pitch, yaw, roll]（度）
     */
    float[] getRotation();

    /**
     * 应用旋转增量（度）
     */
    void rotate(float dpitch, float dyaw, float droll);

    /**
     * 获取当前缩放 [x, y, z]（1 = 原始）
     */
    float[] getScale();

    /**
     * 应用缩放增量
     */
    void scale(float factor);

    /**
     * 取消时恢复快照
     *
     * @param snapshot 开始变换前的快照
     */
    void restore(Object snapshot);

    /**
     * 创建当前状态快照（变换开始时调用）
     */
    Object snapshot();
}
