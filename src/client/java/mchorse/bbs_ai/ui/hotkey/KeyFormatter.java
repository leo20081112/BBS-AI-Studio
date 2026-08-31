package mchorse.bbs_ai.ui.hotkey;

import org.lwjgl.glfw.GLFW;

/**
 * 热键格式化工具
 *
 * <p>把 GLFW 键码 / 修饰位转换为人类可读的显示文本，如
 * {@code GLFW_KEY_G → "G"}、{@code Ctrl+Shift+S → "Ctrl+Shift+S"}、
 * 中键 → "中键"。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class KeyFormatter
{
    private KeyFormatter()
    {}

    /**
     * 格式化热键定义为显示文本（如 "Ctrl+Shift+S"）
     */
    public static String format(HotkeyDefinition definition)
    {
        return formatKeys(definition.key, definition.mods);
    }

    /**
     * 格式化热键的默认键位（自定义热键提示用）
     */
    public static String formatDefault(HotkeyDefinition definition)
    {
        return formatKeys(definition.defaultKey, definition.defaultMods);
    }

    /**
     * 格式化键码 + 修饰位
     */
    public static String formatKeys(int key, int mods)
    {
        StringBuilder builder = new StringBuilder();

        if ((mods & HotkeyDefinition.MOD_CTRL) != 0)
        {
            builder.append("Ctrl+");
        }

        if ((mods & HotkeyDefinition.MOD_SHIFT) != 0)
        {
            builder.append("Shift+");
        }

        if ((mods & HotkeyDefinition.MOD_ALT) != 0)
        {
            builder.append("Alt+");
        }

        builder.append(keyName(key));

        return builder.toString();
    }

    /**
     * GLFW 键码 → 显示名
     */
    public static String keyName(int key)
    {
        /* 鼠标热键约定 */
        if (HotkeyDefinition.isMouseKey(key))
        {
            int button = HotkeyDefinition.mouseButton(key);

            /* -1 表示滚轮 */
            if (button < 0)
            {
                return "滚轮";
            }

            switch (button)
            {
                case 0: return "左键";
                case 1: return "中键";
                case 2: return "右键";
                default: return "鼠标" + (button + 1);
            }
        }

        switch (key)
        {
            case GLFW.GLFW_KEY_SPACE: return "空格";
            case GLFW.GLFW_KEY_LEFT: return "←";
            case GLFW.GLFW_KEY_RIGHT: return "→";
            case GLFW.GLFW_KEY_UP: return "↑";
            case GLFW.GLFW_KEY_DOWN: return "↓";
            case GLFW.GLFW_KEY_ENTER: return "Enter";
            case GLFW.GLFW_KEY_KP_ENTER: return "Enter";
            case GLFW.GLFW_KEY_ESCAPE: return "Esc";
            case GLFW.GLFW_KEY_TAB: return "Tab";
            case GLFW.GLFW_KEY_BACKSPACE: return "Backspace";
            case GLFW.GLFW_KEY_DELETE: return "Delete";
            case GLFW.GLFW_KEY_HOME: return "Home";
            case GLFW.GLFW_KEY_END: return "End";
            case GLFW.GLFW_KEY_LEFT_SHIFT: return "Shift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT: return "Shift";
            case GLFW.GLFW_KEY_LEFT_CONTROL: return "Ctrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL: return "Ctrl";
            case GLFW.GLFW_KEY_LEFT_ALT: return "Alt";
            case GLFW.GLFW_KEY_RIGHT_ALT: return "Alt";
            case GLFW.GLFW_KEY_KP_DECIMAL: return "小键盘.";
            case GLFW.GLFW_KEY_KP_0: return "小键盘0";
            case GLFW.GLFW_KEY_KP_1: return "小键盘1";
            case GLFW.GLFW_KEY_KP_2: return "小键盘2";
            case GLFW.GLFW_KEY_KP_3: return "小键盘3";
            case GLFW.GLFW_KEY_KP_4: return "小键盘4";
            case GLFW.GLFW_KEY_KP_5: return "小键盘5";
            case GLFW.GLFW_KEY_KP_6: return "小键盘6";
            case GLFW.GLFW_KEY_KP_7: return "小键盘7";
            case GLFW.GLFW_KEY_KP_8: return "小键盘8";
            case GLFW.GLFW_KEY_KP_9: return "小键盘9";
            default:
                String name = GLFW.glfwGetKeyName(key, 0);

                if (name != null && !name.isEmpty())
                {
                    return name.toUpperCase();
                }

                return "键" + key;
        }
    }
}
