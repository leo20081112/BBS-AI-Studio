package mchorse.bbs_ai.ui.theme;

/**
 * 可主题化组件接口
 *
 * <p>实现该接口的组件会随主题切换收到 {@link #onThemeChanged()} 回调，
 * 用于刷新自定义绘制配色。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public interface Themed
{
    /**
     * 主题变更回调（主线程）
     */
    void onThemeChanged();
}
