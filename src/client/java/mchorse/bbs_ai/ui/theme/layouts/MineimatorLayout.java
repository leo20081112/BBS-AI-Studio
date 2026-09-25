package mchorse.bbs_ai.ui.theme.layouts;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * Mine-imator 布局
 *
 * <p>扁平化、大面积时间轴的排布：
 * <ul>
 *   <li>顶部：图标工具栏（40px）</li>
 *   <li>左侧：资源库面板（200px）</li>
 *   <li>中央：大面积时间轴（轨道式，占据剩余空间大半）</li>
 *   <li>右侧：窄属性面板（150px）</li>
 *   <li>底部：状态栏（18px）</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class MineimatorLayout implements IUILayout
{
    @Override
    public void arrange(UIContext context, UIElement root, UIElement top, UIElement left, UIElement center, UIElement right, UIElement bottom)
    {
        int topHeight = 40;
        int bottomHeight = 18;
        int libraryWidth = 200;
        int propertyWidth = 150;

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
            left.resetFlex().relative(root).xy(0, topHeight).w(libraryWidth).hTo(bottom != null ? bottom.area : root.area, 0F);
        }

        if (right != null)
        {
            right.resetFlex().relative(root).x(1F, -propertyWidth).y(topHeight).w(propertyWidth).hTo(bottom != null ? bottom.area : root.area, 0F);
        }

        if (center != null)
        {
            /* 中央时间轴为最大区域（Mine-imator 特点） */
            center.resetFlex().relative(root).xy(libraryWidth, topHeight).w(1F, -(libraryWidth + propertyWidth)).hTo(bottom != null ? bottom.area : root.area, 0F);
        }
    }

    @Override
    public String getName()
    {
        return "mineimator";
    }
}
