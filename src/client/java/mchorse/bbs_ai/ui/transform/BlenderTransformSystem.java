package mchorse.bbs_ai.ui.transform;

import org.lwjgl.glfw.GLFW;

import mchorse.bbs_ai.ui.hotkey.HotkeyDefinition;
import mchorse.bbs_ai.ui.hotkey.HotkeyRegistry;
import mchorse.bbs_mod.ui.framework.UIContext;

/**
 * Blender 变换系统（G/R/S 状态机）
 *
 * <p>完整复刻 Blender 变换交互（项目规范模块 10）：
 * <ul>
 *   <li>G = 移动 → 移动鼠标确定位置 → 左键/Enter 确认，右键/Esc 取消</li>
 *   <li>R = 旋转 → 水平拖拽控制角度</li>
 *   <li>S = 缩放 → 水平拖拽控制比例</li>
 *   <li>X / Y / Z = 约束到对应轴（再次按解除）</li>
 *   <li>Shift = 精确模式（速度 1/10）</li>
 *   <li>数字键输入精确值（G → Y → 2.5 → Enter = 沿 Y 移动 2.5 格）</li>
 * </ul></p>
 *
 * <p>宿主面板在 {@code subKeyPressed} / {@code subMouseClicked} /
 * {@code render} 中转发事件给本系统。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BlenderTransformSystem
{
    /**
     * 变换模式
     */
    public enum Mode
    {
        NONE, GRAB, ROTATE, SCALE
    }

    /**
     * 轴约束
     */
    public enum Axis
    {
        NONE, X, Y, Z
    }

    /**
     * 当前变换模式
     */
    private Mode mode = Mode.NONE;

    /**
     * 当前轴约束
     */
    private Axis axis = Axis.NONE;

    /**
     * 变换开始时的鼠标位置
     */
    private int startMouseX;
    private int startMouseY;

    /**
     * 上一帧鼠标位置
     */
    private int lastMouseX;
    private int lastMouseY;

    /**
     * 数字输入缓冲（精确值模式）
     */
    private String numericInput = "";

    /**
     * 变换开始时的快照（取消恢复用）
     */
    private Object snapshot;

    /**
     * 累计变换量
     */
    private float totalDelta;

    /**
     * 变换目标
     */
    private ITransformTarget target;

    /**
     * 设置变换目标
     */
    public void setTarget(ITransformTarget target)
    {
        this.target = target;
    }

    /**
     * 是否处于变换中
     */
    public boolean isTransforming()
    {
        return this.mode != Mode.NONE;
    }

    /**
     * 当前变换模式
     */
    public Mode getMode()
    {
        return this.mode;
    }

    /**
     * 取消变换（对外接口，供 Esc 全局处理）
     */
    public void cancel()
    {
        if (this.isTransforming())
        {
            this.restoreSnapshot();
        }
    }

    /**
     * 键盘事件处理（返回 true 表示已消费）
     */
    public boolean keyPressed(UIContext context)
    {
        HotkeyRegistry registry = HotkeyRegistry.get();

        /* 变换中：确认 / 取消 / 轴约束 / 数字输入 */
        if (this.isTransforming())
        {
            if (context.isPressed(GLFW.GLFW_KEY_ENTER) || context.isPressed(GLFW.GLFW_KEY_KP_ENTER))
            {
                this.applyNumericInput();
                this.confirm();

                return true;
            }

            if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                this.restoreSnapshot();

                return true;
            }

            if (registry.get("transform.axis_x") != null && context.isPressed(GLFW.GLFW_KEY_X))
            {
                this.toggleAxis(Axis.X);

                return true;
            }

            if (context.isPressed(GLFW.GLFW_KEY_Y))
            {
                this.toggleAxis(Axis.Y);

                return true;
            }

            if (context.isPressed(GLFW.GLFW_KEY_Z))
            {
                this.toggleAxis(Axis.Z);

                return true;
            }

            /* 数字键输入精确值 */
            char character = context.getInputCharacter();

            if (Character.isDigit(character) || character == '.' || character == '-')
            {
                this.numericInput += character;

                return true;
            }

            if (context.isPressed(GLFW.GLFW_KEY_BACKSPACE) && !this.numericInput.isEmpty())
            {
                this.numericInput = this.numericInput.substring(0, this.numericInput.length() - 1);

                return true;
            }

            return false;
        }

        /* 非变换中：G / R / S 进入变换 */
        if (this.target == null || !this.target.hasTarget())
        {
            return false;
        }

        if (this.matches(registry, "transform.grab", context))
        {
            return this.begin(Mode.GRAB, context);
        }

        if (this.matches(registry, "transform.rotate", context))
        {
            return this.begin(Mode.ROTATE, context);
        }

        if (this.matches(registry, "transform.scale", context))
        {
            return this.begin(Mode.SCALE, context);
        }

        return false;
    }

    /**
     * 鼠标事件处理：左键确认 / 右键取消（返回 true 表示已消费）
     */
    public boolean mouseClicked(UIContext context)
    {
        if (!this.isTransforming())
        {
            return false;
        }

        if (context.mouseButton == 0)
        {
            this.applyNumericInput();
            this.confirm();

            return true;
        }

        if (context.mouseButton == 1)
        {
            this.restoreSnapshot();

            return true;
        }

        return false;
    }

    /**
     * 每帧鼠标移动处理（变换应用）
     */
    public void updateMouse(UIContext context)
    {
        if (!this.isTransforming() || this.target == null)
        {
            return;
        }

        boolean precise = context.isHeld(GLFW.GLFW_KEY_LEFT_SHIFT) || context.isHeld(GLFW.GLFW_KEY_RIGHT_SHIFT);
        float factor = precise ? 0.1F : 1.0F;

        int dx = context.mouseX - this.lastMouseX;
        int dy = context.mouseY - this.lastMouseY;
        int totalDx = context.mouseX - this.startMouseX;

        this.lastMouseX = context.mouseX;
        this.lastMouseY = context.mouseY;

        /* 数字精确输入激活时不跟随鼠标 */
        if (!this.numericInput.isEmpty())
        {
            return;
        }

        switch (this.mode)
        {
            case GRAB:
            {
                float mx = dx * 0.1F * factor;
                float my = -dy * 0.1F * factor;

                if (this.axis == Axis.NONE)
                {
                    this.target.move(mx, my, 0.0F);
                }
                else if (this.axis == Axis.X)
                {
                    this.target.move(mx, 0.0F, 0.0F);
                }
                else if (this.axis == Axis.Y)
                {
                    this.target.move(0.0F, my, 0.0F);
                }
                else
                {
                    this.target.move(0.0F, 0.0F, mx);
                }

                this.totalDelta += mx;

                break;
            }
            case ROTATE:
            {
                float angle = totalDx * 0.5F * factor;

                this.rotateTo(angle);

                break;
            }
            case SCALE:
            {
                float scale = 1.0F + totalDx * 0.01F * factor;

                this.target.scale(Math.max(0.01F, scale));

                break;
            }
            default:
            {
            }
        }
    }

    /**
     * 当前状态提示文本（状态栏用）
     */
    public String getStatusText()
    {
        if (!this.isTransforming())
        {
            return "";
        }

        String modeName = this.mode == Mode.GRAB ? "移动中" : this.mode == Mode.ROTATE ? "旋转中" : "缩放中";
        String axisName = this.axis == Axis.NONE ? "" : " | 约束: " + this.axis.name();
        String numeric = this.numericInput.isEmpty() ? "" : " | 数值: " + this.numericInput;

        return modeName + " | 左键确认 | 右键取消" + axisName + " | Shift 精确" + numeric;
    }

    /**
     * 进入变换模式
     */
    private boolean begin(Mode mode, UIContext context)
    {
        this.mode = mode;
        this.axis = Axis.NONE;
        this.numericInput = "";
        this.totalDelta = 0;
        this.snapshot = this.target.snapshot();
        this.startMouseX = context.mouseX;
        this.startMouseY = context.mouseY;
        this.lastMouseX = context.mouseX;
        this.lastMouseY = context.mouseY;

        return true;
    }

    /**
     * 确认变换
     */
    private void confirm()
    {
        this.mode = Mode.NONE;
        this.axis = Axis.NONE;
        this.numericInput = "";
        this.snapshot = null;
    }

    /**
     * 取消变换：恢复快照
     */
    private void restoreSnapshot()
    {
        if (this.snapshot != null && this.target != null)
        {
            this.target.restore(this.snapshot);
        }

        this.mode = Mode.NONE;
        this.axis = Axis.NONE;
        this.numericInput = "";
        this.snapshot = null;
    }

    /**
     * 切换轴约束（再按同一轴解除）
     */
    private void toggleAxis(Axis axis)
    {
        this.axis = this.axis == axis ? Axis.NONE : axis;
    }

    /**
     * 应用数字输入的精确值（Enter 时调用）
     */
    private void applyNumericInput()
    {
        if (this.numericInput.isEmpty())
        {
            return;
        }

        try
        {
            float value = Float.parseFloat(this.numericInput);

            if (this.mode == Mode.GRAB)
            {
                if (this.axis == Axis.X)
                {
                    this.target.move(value, 0.0F, 0.0F);
                }
                else if (this.axis == Axis.Y)
                {
                    this.target.move(0.0F, value, 0.0F);
                }
                else if (this.axis == Axis.Z)
                {
                    this.target.move(0.0F, 0.0F, value);
                }
            }
            else if (this.mode == Mode.ROTATE)
            {
                this.rotateTo(value);
            }
            else if (this.mode == Mode.SCALE)
            {
                this.target.scale(Math.max(0.01F, value));
            }
        }
        catch (NumberFormatException ignored)
        {}

        this.numericInput = "";
    }

    /**
     * 旋转到指定角度（相对快照）
     */
    private void rotateTo(float angle)
    {
        /* 先恢复快照再应用绝对角度，避免增量累积漂移 */
        if (this.snapshot != null)
        {
            this.target.restore(this.snapshot);
        }

        float current = this.target.getRotation()[1];

        this.target.rotate(0.0F, angle - current + angle, 0.0F);
    }

    /**
     * 检查上下文按键是否匹配某热键定义
     */
    private boolean matches(HotkeyRegistry registry, String id, UIContext context)
    {
        HotkeyDefinition definition = registry.get(id);

        return definition != null && context.isPressed(definition.key) && !definition.isMouseKey(definition.key)
            && HotkeyDefinition.toMods(0) == definition.mods;
    }
}
