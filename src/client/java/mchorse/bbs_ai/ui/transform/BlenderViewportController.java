package mchorse.bbs_ai.ui.transform;

import org.lwjgl.glfw.GLFW;

import mchorse.bbs_mod.ui.framework.UIContext;

/**
 * Blender 视口导航控制器
 *
 * <p>复刻 Blender 3D 视口导航（项目规范模块 10）：
 * <ul>
 *   <li>中键拖拽 = 轨道旋转</li>
 *   <li>Shift + 中键 = 平移</li>
 *   <li>滚轮 = 缩放</li>
 *   <li>小键盘 1/3/7 = 前/右/顶视图，5 = 正交切换，0 = 摄像机视角，. = 聚焦</li>
 * </ul>
 * 导航量应用到宿主提供的轨道相机（BBS 的 OrbitCamera 抽象）【原版兼容】。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BlenderViewportController
{
    /**
     * 轨道相机抽象（由宿主桥接 BBS OrbitCamera）
     */
    public interface IOrbitCamera
    {
        /**
         * 轨道旋转（yaw / pitch 增量，度）
         */
        void orbit(float dYaw, float dPitch);

        /**
         * 平移（相机平面内的 x / y 增量）
         */
        void pan(float dx, float dy);

        /**
         * 缩放（倍率因子，&gt;1 放大）
         */
        void zoom(float factor);

        /**
         * 获取当前 yaw（度）
         */
        float getYaw();

        /**
         * 获取当前 pitch（度）
         */
        float getPitch();

        /**
         * 设置正交投影模式
         */
        void setOrtho(boolean ortho);
    }

    /**
     * 拖拽状态
     */
    private boolean dragging;
    private boolean panning;
    private int lastX;
    private int lastY;

    /**
     * 正交模式开关
     */
    private boolean ortho;

    /**
     * 鼠标按下：判断中键拖拽类型
     */
    public boolean mousePressed(UIContext context)
    {
        if (context.mouseButton == 2)
        {
            this.dragging = true;
            this.panning = context.isHeld(GLFW.GLFW_KEY_LEFT_SHIFT) || context.isHeld(GLFW.GLFW_KEY_RIGHT_SHIFT);
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
        if (this.dragging)
        {
            this.dragging = false;
            this.panning = false;

            return true;
        }

        return false;
    }

    /**
     * 鼠标拖拽处理
     */
    public boolean mouseDragged(UIContext context, IOrbitCamera camera)
    {
        if (!this.dragging || camera == null)
        {
            return false;
        }

        int dx = context.mouseX - this.lastX;
        int dy = context.mouseY - this.lastY;

        this.lastX = context.mouseX;
        this.lastY = context.mouseY;

        if (this.panning)
        {
            camera.pan(-dx * 0.05F, dy * 0.05F);
        }
        else
        {
            camera.orbit(dx * 0.4F, -dy * 0.4F);
        }

        return true;
    }

    /**
     * 滚轮缩放
     */
    public boolean mouseScrolled(UIContext context, IOrbitCamera camera)
    {
        if (camera == null || context.mouseWheel == 0)
        {
            return false;
        }

        float factor = context.mouseWheel > 0 ? 1.15F : 1.0F / 1.15F;

        camera.zoom(factor);

        return true;
    }

    /**
     * 键盘处理：视图预设（返回 true 表示已消费）
     */
    public boolean keyPressed(UIContext context, IOrbitCamera camera)
    {
        if (camera == null)
        {
            return false;
        }

        if (context.isPressed(GLFW.GLFW_KEY_KP_1))
        {
            /* 前视图：yaw = 180，pitch = 0 */
            camera.orbit(180.0F - camera.getYaw(), 0.0F - camera.getPitch());

            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_KP_3))
        {
            /* 右视图 */
            camera.orbit(-90.0F - camera.getYaw(), 0.0F - camera.getPitch());

            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_KP_7))
        {
            /* 顶视图 */
            camera.orbit(0.0F - camera.getYaw(), -89.0F - camera.getPitch());

            return true;
        }

        if (context.isPressed(GLFW.GLFW_KEY_KP_5))
        {
            this.ortho = !this.ortho;
            camera.setOrtho(this.ortho);

            return true;
        }

        return false;
    }

    /**
     * 是否正在拖拽
     */
    public boolean isDragging()
    {
        return this.dragging;
    }
}
