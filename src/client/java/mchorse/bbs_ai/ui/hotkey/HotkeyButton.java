package mchorse.bbs_ai.ui.hotkey;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;

import java.util.function.Consumer;

/**
 * 带热键提示的按钮
 *
 * <p>在按钮文本右侧以灰色小字追加当前热键（如 "移动 (G)"），
 * 悬停时 Tooltip 显示详细描述 + 默认热键（若已被修改）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class HotkeyButton extends UIButton
{
    /**
     * 关联的热键定义
     */
    private final HotkeyDefinition hotkey;

    public HotkeyButton(HotkeyDefinition hotkey, Consumer<UIButton> callback)
    {
        super(IKey.constant(buildLabel(hotkey)), callback);

        this.hotkey = hotkey;
        this.tooltip(IKey.constant(buildTooltip(hotkey)));
    }

    /**
     * 构建按钮标签：名称 + 热键提示
     */
    private static String buildLabel(HotkeyDefinition hotkey)
    {
        return hotkey.name + " (" + KeyFormatter.format(hotkey) + ")";
    }

    /**
     * 构建 Tooltip：描述 + 已修改时的默认热键
     */
    private static String buildTooltip(HotkeyDefinition hotkey)
    {
        StringBuilder builder = new StringBuilder(hotkey.description);

        if (hotkey.isModified())
        {
            builder.append("\n默认：").append(KeyFormatter.formatDefault(hotkey));
        }

        return builder.toString();
    }

    /**
     * 获取关联热键
     */
    public HotkeyDefinition getHotkey()
    {
        return this.hotkey;
    }

    /**
     * 渲染前刷新标签（保证显示最新热键）
     */
    @Override
    public void render(UIContext context)
    {
        this.label = IKey.constant(buildLabel(this.hotkey));

        super.render(context);
    }
}
