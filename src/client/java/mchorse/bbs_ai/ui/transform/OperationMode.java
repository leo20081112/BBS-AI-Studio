package mchorse.bbs_ai.ui.transform;

/**
 * 操作模式（项目规范模块 10 分层模式）
 *
 * <ul>
 *   <li>{@link #BBS_COMPATIBLE} —— 原版 BBS 操作（默认，老用户无感知）</li>
 *   <li>{@link #BLENDER_STYLE} —— Blender 热键映射（G/R/S、中键导航、I 关键帧）</li>
 *   <li>{@link #MOTION_ILLUSTRATOR} —— Mine-imator 操作风格（选中即 Gizmo 拖拽）</li>
 * </ul>
 *
 * <p>作者：BBS AI Studio</p>
 */
public enum OperationMode
{
    BBS_COMPATIBLE,
    BLENDER_STYLE,
    MOTION_ILLUSTRATOR;

    /**
     * 由设置索引解析
     */
    public static OperationMode byIndex(int index)
    {
        OperationMode[] values = values();

        return values[Math.max(0, Math.min(values.length - 1, index))];
    }

    /**
     * 是否为 Blender 风格（G/R/S 变换 + 中键导航）
     */
    public boolean isBlenderStyle()
    {
        return this == BLENDER_STYLE;
    }

    /**
     * 是否为 Mine-imator 风格（Gizmo 直接拖拽）
     */
    public boolean isMineimatorStyle()
    {
        return this == MOTION_ILLUSTRATOR;
    }
}
