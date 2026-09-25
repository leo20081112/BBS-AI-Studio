package mchorse.bbs_ai.ui.theme.layouts;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * Blender 深色布局
 *
 * <p>复刻 Blender 工作区排布：
 * <ul>
 *   <li>顶部：菜单栏（File / Edit / Timeline / Render / AI Tools，28px）</li>
 *   <li>左侧：可折叠工具架（T 键切换，180px）</li>
 *   <li>中央：3D 视口（最大化区域）</li>
 *   <li>右侧：可折叠 N-Panel（N 键切换，220px）</li>
 *   <li>底部：时间轴区域（可缩放，120px）</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BlenderDarkLayout implements IUILayout
{
    /**
     * 工具架 / N-Panel 是否展开（由 T / N 热键切换）
     */
    public boolean shelfVisible = true;
    public boolean nPanelVisible = true;

    @Override
    public void arrange(UIContext context, UIElement root, UIElement top, UIElement left, UIElement center, UIElement right, UIElement bottom)
    {
        int topHeight = 28;
        int bottomHeight = 120;
        int shelfWidth = this.shelfVisible ? 180 : 0;
        int nPanelWidth = this.nPanelVisible ? 220 : 0;

        if (top != null)
        {
            top.resetFlex().relative(root).xy(0, 0).w(1F).h(topHeight);
        }

        if (bottom != null)
        {
            bottom.resetFlex().relative(root).y(1F, -bottomHeight).w(1F).h(bottomHeight);
        }

        if (left != null)
        {
            left.resetFlex().relative(root).xy(0, topHeight).w(shelfWidth).hTo(bottom != null ? bottom.area : root.area, 0F);
            left.setVisible(this.shelfVisible);
        }

        if (right != null)
        {
            right.resetFlex().relative(root).x(1F, -nPanelWidth).y(topHeight).w(nPanelWidth).hTo(bottom != null ? bottom.area : root.area, 0F);
            right.setVisible(this.nPanelVisible);
        }

        if (center != null)
        {
            center.resetFlex().relative(root).xy(shelfWidth, topHeight).w(1F, -(shelfWidth + nPanelWidth)).hTo(bottom != null ? bottom.area : root.area, 0F);
        }
    }

    @Override
    public String getName()
    {
        return "blender_dark";
    }
}
