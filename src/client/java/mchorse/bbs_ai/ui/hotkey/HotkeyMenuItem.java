package mchorse.bbs_ai.ui.hotkey;

/**
 * 带热键提示的菜单项
 *
 * <p>菜单 / 列表行内右侧对齐显示热键的轻量数据项。渲染由宿主列表
 * （如热键设置面板的 UIStringList 子类）完成：左侧动作名、右侧热键文本。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class HotkeyMenuItem
{
    /**
     * 关联热键定义
     */
    public final HotkeyDefinition hotkey;

    public HotkeyMenuItem(HotkeyDefinition hotkey)
    {
        this.hotkey = hotkey;
    }

    /**
     * 行显示文本：动作名
     */
    public String getLabel()
    {
        return this.hotkey.name;
    }

    /**
     * 行右侧对齐的热键文本
     */
    public String getHotkeyText()
    {
        return KeyFormatter.format(this.hotkey);
    }

    /**
     * 分类显示名
     */
    public String getCategoryTitle()
    {
        return this.hotkey.category.title;
    }

    /**
     * 是否被修改
     */
    public boolean isModified()
    {
        return this.hotkey.isModified();
    }
}
