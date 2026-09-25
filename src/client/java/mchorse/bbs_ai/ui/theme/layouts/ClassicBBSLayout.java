package mchorse.bbs_ai.ui.theme.layouts;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * 经典 BBS 布局（默认，保持原版观感）【原版兼容】
 *
 * <p>不做额外重排：顶部工具条 24px、底部状态栏 16px、左右面板按内容自适应，
 * 中央区域占据剩余空间——与 BBS 原版面板排布一致。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ClassicBBSLayout implements IUILayout
{
    @Override
    public void arrange(UIContext context, UIElement root, UIElement top, UIElement left, UIElement center, UIElement right, UIElement bottom)
    {
        if (top != null)
        {
            top.resetFlex().relative(root).xy(0, 0).w(1F).h(24);
        }

        if (bottom != null)
        {
            bottom.resetFlex().relative(root).y(1F, -16).w(1F).h(16);
        }

        if (left != null)
        {
            left.resetFlex().relative(root).xy(0, 24).w(160).hTo(bottom != null ? bottom.area : root.area, 0F);
        }

        if (right != null)
        {
            right.resetFlex().relative(root).x(1F, -200).y(24).w(200).hTo(bottom != null ? bottom.area : root.area, 0F);
        }

        if (center != null)
        {
            int leftEdge = left != null ? 160 : 0;
            int rightEdge = right != null ? 200 : 0;

            center.resetFlex().relative(root).xy(leftEdge, 24).w(1F, -(leftEdge + rightEdge)).hTo(bottom != null ? bottom.area : root.area, 0F);
        }
    }

    @Override
    public String getName()
    {
        return "classic_bbs";
    }
}
