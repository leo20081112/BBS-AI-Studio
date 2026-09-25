package mchorse.bbs_ai.ui.theme.layouts;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

/**
 * UI 布局接口（项目规范模块 9）
 *
 * <p>每种主题提供一份布局方案，负责对 AI 面板区域内的组件进行重排。
 * {@code arrange} 在主题切换 / 面板 resize 时调用。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public interface IUILayout
{
    /**
     * 排列组件
     *
     * @param context   UI 上下文
     * @param root      面板根元素（布局的参照区域）
     * @param top       顶部工具条（菜单栏 / 图标工具栏），可为 null
     * @param left      左侧面板（工具架 / 资源库），可为 null
     * @param center    中央区域
     * @param right     右侧属性面板（N-Panel），可为 null
     * @param bottom    底部区域（时间轴 / 状态栏），可为 null
     */
    void arrange(UIContext context, UIElement root, UIElement top, UIElement left, UIElement center, UIElement right, UIElement bottom);

    /**
     * 布局名称（调试显示）
     */
    String getName();
}
