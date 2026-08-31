package mchorse.bbs_ai.ui.transform;

import org.lwjgl.glfw.GLFW;

import mchorse.bbs_mod.ui.framework.UIContext;

/**
 * Mine-imator 风格变换系统
 *
 * <p>复刻 Mine-imator 的「选中即 Gizmo、直接拖拽」操作（项目规范模块 10）：
 * <ul>
 *   <li>选中即显示 Gizmo（移动箭头 / 旋转圆环 / 缩放角点）</li>
 *   <li>直接拖拽 Gizmo 操作，无需 G/R/S 热键</li>
 *   <li>拖拽量直接作用于 {@link ITransformTarget}</li>
 * </ul>
 * 屏幕空间 Gizmo 的命中测试与拖拽增量计算由本类完成；
 * 3D 投影由宿主提供（视口内目标的屏幕坐标）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class MineimatorTransformSystem
{
    /**
     * Gizmo 模式
     */
    public enum GizmoMode
    {
        MOVE, ROTATE, SCALE
    }

    /**
     * 拖拽热区半径（像素）
     */
    private static final int HANDLE_RADIUS = 8;

    /**
     * 当前 Gizmo 模式
     */
    private GizmoMode mode = GizmoMode.MOVE;

    /**
     * 正在拖拽的轴：-1 无，0/1/2 = X/Y/Z
     */
    private int draggingAxis = -1;

    /**
     * 拖拽开始鼠标位置
     */
    private int startMouseX;
    private int startMouseY;

    /**
     * 上次鼠标位置
     */
    private int lastMouseX;
    private int lastMouseY;

    /**
     * 变换目标
     */
    private ITransformTarget target;

    /**
     * 目标的屏幕坐标（由宿主每帧更新；无 3D 投影时为面板中心）
     */
    private int targetScreenX;
    private int targetScreenY;

    /**
     * 设置变换目标
     */
    public void setTarget(ITransformTarget target)
    {
        this.target = target;
    }

    /**
     * 更新目标屏幕坐标（宿主每帧调用）
     */
    public void updateTargetScreen(int x, int y)
    {
        this.targetScreenX = x;
        this.targetScreenY = y;
    }

    /**
     * 设置 Gizmo 模式
     */
    public void setMode(GizmoMode mode)
    {
        this.mode = mode == null ? GizmoMode.MOVE : mode;
    }

    /**
     * 获取当前模式
     */
    public GizmoMode getMode()
    {
        return this.mode;
    }

    /**
     * 鼠标按下：命中测试 Gizmo 手柄
     *
     * @return 是否命中并开始拖拽
     */
    public boolean mousePressed(UIContext context)
    {
        if (this.target == null || !this.target.hasTarget())
        {
            return false;
        }

        int axis = this.pickHandle(context.mouseX, context.mouseY);

        if (axis >= 0)
        {
            this.draggingAxis = axis;
            this.startMouseX = context.mouseX;
            this.startMouseY = context.mouseY;
            this.lastMouseX = context.mouseX;
            this.lastMouseY = context.mouseY;

            return true;
        }

        return false;
    }

    /**
     * 鼠标释放
     */
    public boolean mouseReleased(UIContext context)
    {
        if (this.draggingAxis >= 0)
        {
            this.draggingAxis = -1;

            return true;
        }

        return false;
    }

    /**
     * 鼠标拖拽
     */
    public boolean mouseDragged(UIContext context)
    {
        if (this.draggingAxis < 0 || this.target == null)
        {
            return false;
        }

        int dx = context.mouseX - this.lastMouseX;
        int dy = context.mouseY - this.lastMouseY;

        this.lastMouseX = context.mouseX;
        this.lastMouseY = context.mouseY;

        boolean precise = context.isHeld(GLFW.GLFW_KEY_LEFT_SHIFT) || context.isHeld(GLFW.GLFW_KEY_RIGHT_SHIFT);
        float factor = precise ? 0.02F : 0.2F;

        switch (this.mode)
        {
            case MOVE: this.target.move(this.draggingAxis == 0 ? dx * factor : 0.0F, this.draggingAxis == 1 ? -dy * factor : 0.0F, this.draggingAxis == 2 ? dx * factor : 0.0F); break;
            case ROTATE: this.target.rotate(0.0F, dx * 0.5F, 0.0F); break;
            case SCALE: this.target.scale(1.0F + dx * 0.01F); break;
        }

        return true;
    }

    /**
     * 命中测试：返回命中的轴（-1 未命中）
     */
    private int pickHandle(int mouseX, int mouseY)
    {
        for (int axis = 0; axis < 3; axis++)
        {
            int hx = this.targetScreenX + (axis == 0 ? 40 : 0);
            int hy = this.targetScreenY - (axis == 1 ? 40 : 0);

            /* Z 轴手柄画在对角线方向 */
            if (axis == 2)
            {
                hx = this.targetScreenX + 28;
                hy = this.targetScreenY + 28;
            }

            if (Math.abs(mouseX - hx) <= HANDLE_RADIUS && Math.abs(mouseY - hy) <= HANDLE_RADIUS)
            {
                return axis;
            }
        }

        return -1;
    }

    /**
     * 当前状态提示（状态栏用）
     */
    public String getStatusText()
    {
        String modeName = this.mode == GizmoMode.MOVE ? "移动" : this.mode == GizmoMode.ROTATE ? "旋转" : "缩放";

        return this.draggingAxis >= 0 ? "拖拽中（" + modeName + "）" : "Gizmo: " + modeName + "（拖拽箭头操作）";
    }
}
