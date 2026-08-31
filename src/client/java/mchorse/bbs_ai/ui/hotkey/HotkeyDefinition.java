package mchorse.bbs_ai.ui.hotkey;

import org.lwjgl.glfw.GLFW;

/**
 * 热键定义
 *
 * <p>描述一个可自定义热键的动作：动作 ID、中英文显示名、默认热键、
 * 当前热键、分类与详细描述。热键 = GLFW 键码 + 修饰位（shift/ctrl/alt）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class HotkeyDefinition
{
    /**
     * 修饰位常量
     */
    public static final int MOD_SHIFT = 1;
    public static final int MOD_CTRL = 2;
    public static final int MOD_ALT = 4;

    /**
     * 热键分类
     */
    public enum Category
    {
        VIEW("视图"),
        TRANSFORM("变换"),
        KEYFRAME("关键帧"),
        TIMELINE("时间轴"),
        PANEL("面板"),
        MODE("模式"),
        AI("AI"),
        GENERAL("通用");

        /**
         * 中文分类名
         */
        public final String title;

        Category(String title)
        {
            this.title = title;
        }
    }

    /**
     * 动作 ID（如 "transform.grab"）
     */
    public final String id;

    /**
     * 中文显示名
     */
    public final String name;

    /**
     * 详细描述
     */
    public final String description;

    /**
     * 分类
     */
    public final Category category;

    /**
     * 默认 GLFW 键码（鼠标热键用负数约定：-1000 - button）
     */
    public final int defaultKey;

    /**
     * 默认修饰位
     */
    public final int defaultMods;

    /**
     * 当前 GLFW 键码（可自定义）
     */
    public int key;

    /**
     * 当前修饰位
     */
    public int mods;

    public HotkeyDefinition(String id, String name, String description, Category category, int defaultKey, int defaultMods)
    {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.defaultKey = defaultKey;
        this.defaultMods = defaultMods;
        this.key = defaultKey;
        this.mods = defaultMods;
    }

    /**
     * 是否已被用户修改
     */
    public boolean isModified()
    {
        return this.key != this.defaultKey || this.mods != this.defaultMods;
    }

    /**
     * 恢复默认
     */
    public void reset()
    {
        this.key = this.defaultKey;
        this.mods = this.defaultMods;
    }

    /**
     * 与给定键位匹配（含修饰位，忽略大小写修饰差异）
     */
    public boolean matches(int keyCode, int modifiers)
    {
        return this.key == keyCode && this.mods == this.toMods(modifiers);
    }

    /**
     * GLFW 修饰位 → 我们的修饰位
     */
    public static int toMods(int glfwMods)
    {
        int mods = 0;

        if ((glfwMods & GLFW.GLFW_MOD_SHIFT) != 0)
        {
            mods |= MOD_SHIFT;
        }

        if ((glfwMods & GLFW.GLFW_MOD_CONTROL) != 0)
        {
            mods |= MOD_CTRL;
        }

        if ((glfwMods & GLFW.GLFW_MOD_ALT) != 0)
        {
            mods |= MOD_ALT;
        }

        return mods;
    }

    /**
     * 鼠标热键键码约定：-1000 - 鼠标按键号
     */
    public static int mouseKey(int button)
    {
        return -1000 - button;
    }

    /**
     * 是否为鼠标热键
     */
    public static boolean isMouseKey(int key)
    {
        return key <= -1000;
    }

    /**
     * 从鼠标热键键码还原按键号
     */
    public static int mouseButton(int key)
    {
        return -1000 - key;
    }
}
