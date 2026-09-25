package mchorse.bbs_ai.ui.ai;

import java.util.ArrayList;
import java.util.List;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * 面板 AI 助手动作菜单（各原生面板动作栏的 AI 按钮点开的弹窗）
 *
 * <p>内容 = 场景说明 + 面板专属 AI 动作按钮列表。动作由宿主面板注入，
 * 与面板当前上下文（当前影片/当前模型）联动。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class UIAIAssistMenu extends UIOverlayPanel
{
    /**
     * 单个动作项
     */
    public static class Entry
    {
        public final String label;
        public final Runnable action;

        public Entry(String label, Runnable action)
        {
            this.label = label;
            this.action = action;
        }
    }

    /**
     * 构建一个 AI 助手菜单
     *
     * @param title    弹窗标题（如「影片编辑 AI 助手」）
     * @param hint     场景说明（灰字，可空串）
     * @param entries  动作列表
     */
    public UIAIAssistMenu(String title, String hint, List<Entry> entries)
    {
        super(IKey.constant(title));

        mchorse.bbs_mod.ui.framework.elements.UIScrollView scroll = UI.scrollView();
        scroll.relative(this.content).xy(0, 0).w(1F).h(1F);

        if (hint != null && !hint.isEmpty())
        {
            UILabel hintLabel = UI.label(IKey.constant(hint), 14, Colors.GRAY);

            scroll.add(hintLabel);
        }

        for (Entry entry : entries)
        {
            scroll.add(new UIButton(IKey.constant(entry.label), (b) ->
            {
                this.close();
                entry.action.run();
            }));
        }

        this.content.add(scroll);
    }

    /**
     * 弹窗封装：在面板上下文弹出 AI 助手菜单
     */
    public static void open(mchorse.bbs_mod.ui.framework.UIContext context, String title, String hint, List<Entry> entries)
    {
        mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay.addOverlay(
            context,
            new UIAIAssistMenu(title, hint, entries),
            300,
            Math.min(360, 120 + entries.size() * 26)
        );
    }

    /**
     * 影片编辑器的 AI 助手动作
     */
    public static List<Entry> filmEntries(UIDashboard dashboard)
    {
        List<Entry> entries = new ArrayList<>();

        entries.add(new Entry(L10n.lang("bbs_ai.assist.film.open_editor").get(), () ->
        {
            mchorse.bbs_ai.ui.editor.UIAIEditorPanel panel = dashboard.getPanel(mchorse.bbs_ai.ui.editor.UIAIEditorPanel.class);

            if (panel != null)
            {
                dashboard.setPanel(panel);
            }
        }));

        entries.add(new Entry(L10n.lang("bbs_ai.assist.film.analyze").get(), () ->
        {
            mchorse.bbs_ai.integration.BBSAIClientIntegration.showSection("mods");
        }));

        return entries;
    }
}
