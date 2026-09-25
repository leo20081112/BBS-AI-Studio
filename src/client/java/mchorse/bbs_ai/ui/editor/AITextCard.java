package mchorse.bbs_ai.ui.editor;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.UI;

/**
 * Codex 风格文本卡片（AI 编辑器对话与工具日志共用）
 *
 * <p>结构：可选角色/语言标头（小号彩色单行）+ 正文（自动换行，永不截断）+
 * 左侧强调色条 + 卡片底色。代码块变体用更暗的底色与语言标头，
 * 视觉上区分普通叙述与代码/数据。</p>
 *
 * <p>注意：卡片自身是纵向 column（高度 = 内容高度），必须带
 * {@code .height(20)} 给子元素默认高度（同上游 UISection.fields）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class AITextCard extends UIElement
{
    /**
     * @param header      标头（角色名/语言名），null = 无标头
     * @param body        正文（UIText 自动换行）
     * @param headerColor 标头颜色
     * @param bodyColor   正文颜色
     * @param bg          卡片底色（0 = 透明）
     * @param accent      左侧强调色条（0 = 无色条）
     */
    public AITextCard(String header, String body, int headerColor, int bodyColor, int bg, int accent)
    {
        this.column(0).stretch().vertical().height(20).padding(1);

        if (header != null && !header.isEmpty())
        {
            UILabel label = UI.label(IKey.constant(header), 10, headerColor);

            label.margin(10, 3, 8, 0);
            this.add(label);
        }

        UIText text = new UIText(body);

        text.color(bodyColor, true).padding(accent != 0 ? 10 : 8, 3);
        this.add(text);

        this.bg = bg;
        this.accent = accent;
    }

    private final int bg;
    private final int accent;

    @Override
    public void render(UIContext context)
    {
        if (this.bg != 0)
        {
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), this.bg);
        }

        if (this.accent != 0)
        {
            context.batcher.box(this.area.x, this.area.y, this.area.x + 2, this.area.ey(), this.accent);
        }

        super.render(context);
    }
}
