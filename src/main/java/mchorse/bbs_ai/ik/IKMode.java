package mchorse.bbs_ai.ik;

/**
 * IK 工作模式
 *
 * <p>对应设置中的「IK 模式」下拉项：
 * <ul>
 *   <li>{@link #NATIVE} —— BBS 原生 IK 行为</li>
 *   <li>{@link #BLENDER} —— Blender 风格 IK（完全复刻 Blender IK 约束语义）</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public enum IKMode
{
    NATIVE,
    BLENDER
}
