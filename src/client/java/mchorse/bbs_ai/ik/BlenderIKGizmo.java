package mchorse.bbs_ai.ik;

import org.joml.Vector3f;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * Blender IK 交互 Gizmo（2D 屏幕空间）
 *
 * <p>复刻 Blender Empty 风格的交互物（项目规范模块 8「3D Gizmo 交互」）：
 * <ul>
 *   <li>目标空物体：黄色线框 + 十字线</li>
 *   <li>极向目标：蓝色圆点 + 方向线</li>
 *   <li>拖拽目标 → 实时更新约束目标位置 → 触发宿主重新解算 → 实时预览</li>
 *   <li>拖拽极向目标 → 实时调整弯曲方向</li>
 * </ul>
 * 3D → 2D 投影由宿主提供（每帧更新 gizmo 屏幕坐标）；
 * 拖拽增量转换为世界坐标增量（相机朝向近似），作用于约束数据。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BlenderIKGizmo
{
    /**
     * 关联的 IK 组件
     */
    private final BlenderIKComponent component;

    /**
     * 目标屏幕坐标（宿主每帧更新）
     */
    private int targetScreenX;
    private int targetScreenY;

    /**
     * 极向目标屏幕坐标
     */
    private int poleScreenX;
    private int poleScreenY;

    /**
     * 拖拽状态：0 无，1 目标，2 极向
     */
    private int dragging;

    /**
     * 上次鼠标位置
     */
    private int lastX;
    private int lastY;

    /**
     * 拖拽回调（宿主重新解算）
     */
    private Runnable onSolve;

    public BlenderIKGizmo(BlenderIKComponent component)
    {
        this.component = component;
    }

    /**
     * 设置解算回调
     */
    public void setOnSolve(Runnable callback)
    {
        this.onSolve = callback;
    }

    /**
     * 更新屏幕坐标（宿主每帧调用）
     */
    public void updateScreens(int targetX, int targetY, int poleX, int poleY)
    {
        this.targetScreenX = targetX;
        this.targetScreenY = targetY;
        this.poleScreenX = poleX;
        this.poleScreenY = poleY;
    }

    /**
     * 鼠标按下：命中测试目标 / 极向手柄
     */
    public boolean mousePressed(UIContext context)
    {
        if (this.isInside(this.targetScreenX, this.targetScreenY, context.mouseX, context.mouseY, 10))
        {
            this.dragging = 1;
            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            return true;
        }

        if (this.component.constraint.poleTarget != null
            && this.isInside(this.poleScreenX, this.poleScreenY, context.mouseX, context.mouseY, 10))
        {
            this.dragging = 2;
            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            return true;
        }

        return false;
    }

    /**
     * 鼠标释放
     */
    public boolean mouseReleased(UIContext context)
    {
        if (this.dragging > 0)
        {
            this.dragging = 0;

            /* 释放后进入预烘焙预览（宿主回调） */
            if (this.onSolve != null)
            {
                this.onSolve.run();
            }

            return true;
        }

        return false;
    }

    /**
     * 鼠标拖拽：屏幕位移 → 世界坐标增量（XZ 平面 + Shift 调 Y）
     */
    public boolean mouseDragged(UIContext context)
    {
        if (this.dragging == 0)
        {
            return false;
        }

        int dx = context.mouseX - this.lastX;
        int dy = context.mouseY - this.lastY;

        this.lastX = context.mouseX;
        this.lastY = context.mouseY;

        boolean vertical = context.isHeld(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT);
        float factor = 0.05F;

        Vector3f offset = vertical
            ? new Vector3f(0.0F, dy * factor, 0.0F)
            : new Vector3f(dx * factor, 0.0F, dy * factor);

        if (this.dragging == 1)
        {
            this.component.constraint.target.add(offset);
        }
        else
        {
            if (this.component.constraint.poleTarget == null)
            {
                this.component.constraint.poleTarget = new Vector3f();
            }

            this.component.constraint.poleTarget.add(offset);
        }

        /* 拖拽过程中实时解算 → 实时预览 */
        if (this.onSolve != null)
        {
            this.onSolve.run();
        }

        return true;
    }

    /**
     * 渲染 Gizmo（宿主面板 render 内调用）
     */
    public void render(UIContext context)
    {
        Batcher2D batcher = context.batcher;
        int tx = this.targetScreenX;
        int ty = this.targetScreenY;

        /* 目标：黄色线框 + 十字线（Blender Empty 样式） */
        int yellow = Colors.A100 | 0xFFE000;

        batcher.box(tx - 8, ty - 1, tx + 8, ty + 1, yellow);
        batcher.box(tx - 1, ty - 8, tx + 1, ty + 8, yellow);
        batcher.box(tx - 8, ty - 8, tx + 8, ty - 6, yellow);
        batcher.box(tx - 8, ty + 6, tx + 8, ty + 8, yellow);
        batcher.box(tx - 8, ty - 8, tx - 6, ty + 8, yellow);
        batcher.box(tx + 6, ty - 8, tx + 8, ty + 8, yellow);

        /* 极向目标：蓝色圆点 + 方向线 */
        if (this.component.constraint.poleTarget != null)
        {
            int blue = Colors.A100 | 0x4090FF;
            int px = this.poleScreenX;
            int py = this.poleScreenY;

            /* 目标 → 极向方向线 */
            batcher.box(Math.min(tx, px), Math.min(ty, py), Math.min(tx, px) + 1, Math.min(ty, py) + 1, blue);
            batcher.box(px - 4, py - 1, px + 4, py + 1, blue);
            batcher.box(px - 1, py - 4, px + 1, py + 4, blue);
        }

        /* 拖拽中提示 */
        if (this.dragging > 0)
        {
            String hint = this.dragging == 1 ? "拖拽 IK 目标（Shift 调整高度）" : "拖拽极向目标（Shift 调整高度）";

            batcher.box(tx + 12, ty - 8, tx + 12 + batcher.getFont().getWidth(hint) + 8, ty + 6, 0x88000000);
            batcher.text(hint, tx + 16, ty - 4, Colors.WHITE);
        }
    }

    /**
     * 区域命中测试
     */
    private boolean isInside(int hx, int hy, int x, int y, int radius)
    {
        return Math.abs(x - hx) <= radius && Math.abs(y - hy) <= radius;
    }
}
