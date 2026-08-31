package mchorse.bbs_ai.ui.theme;

import mchorse.bbs_ai.ui.theme.layouts.IUILayout;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.utils.colors.Colors;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 主题管理器（客户端，单例）
 *
 * <p>管理三套 UI 主题的切换与资源加载：
 * <ul>
 *   <li>{@code CLASSIC_BBS} —— 原版 BBS FS 风格（默认，不动 BBS 自身配色）</li>
 *   <li>{@code BLENDER_DARK} —— Blender 深色主题（AI 面板区域）</li>
 *   <li>{@code MINEIMATOR} —— Mine-imator 扁平风格（AI 面板区域）</li>
 * </ul>
 * 主题资源（colors.json / layout.json）从 {@code config/bbs/ai_themes/<id>/} 加载，
 * 缺失时使用内置默认值。{@link #setTheme(UITheme)} 触发监听器回调与
 * {@code ThemeChangeEvent} 事件，即时生效并持久化到设置。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ThemeManager
{
    /**
     * 单例
     */
    private static ThemeManager instance;

    /**
     * 当前主题
     */
    private UITheme theme = UITheme.CLASSIC_BBS;

    /**
     * 主题资源包
     */
    private ThemeResourcePack resourcePack = new ThemeResourcePack();

    /**
     * 布局方案（随主题切换）
     */
    private IUILayout layout;

    /**
     * 主题变更监听器
     */
    private final List<Consumer<UITheme>> listeners = new ArrayList<>();

    /**
     * 初始化（读取设置中的主题索引）
     */
    public static synchronized void initialize()
    {
        if (instance == null)
        {
            instance = new ThemeManager();
            instance.setTheme(UITheme.byIndex(mchorse.bbs_ai.core.BBSAISettings.uiTheme.get()), false);
        }
    }

    /**
     * 获取单例
     */
    public static ThemeManager get()
    {
        if (instance == null)
        {
            initialize();
        }

        return instance;
    }

    /**
     * 订阅主题变更
     */
    public void addListener(Consumer<UITheme> listener)
    {
        this.listeners.add(listener);
    }

    /**
     * 获取当前主题
     */
    public UITheme getTheme()
    {
        return this.theme;
    }

    /**
     * 获取主题资源包
     */
    public ThemeResourcePack getPack()
    {
        return this.resourcePack;
    }

    /**
     * 获取当前布局
     */
    public IUILayout getLayout()
    {
        return this.layout;
    }

    /**
     * 切换主题（即时生效 + 持久化 + 事件广播）
     */
    public void setTheme(UITheme newTheme)
    {
        this.setTheme(newTheme, true);
    }

    /**
     * 切换主题内部实现
     *
     * @param notify 是否广播事件与持久化（初始化时为 false）
     */
    private void setTheme(UITheme newTheme, boolean notify)
    {
        if (newTheme == null)
        {
            newTheme = UITheme.CLASSIC_BBS;
        }

        UITheme oldTheme = this.theme;

        if (oldTheme == newTheme && this.resourcePack.isLoaded())
        {
            return;
        }

        this.theme = newTheme;
        this.resourcePack.load(newTheme);
        this.layout = this.createLayout(newTheme);

        /* 持久化到设置 */
        if (notify && mchorse.bbs_ai.core.BBSAISettings.uiTheme != null)
        {
            mchorse.bbs_ai.core.BBSAISettings.uiTheme.set(newTheme.ordinal());
        }

        /* 通知监听器（Themed 组件重绘 / 重排） */
        for (Consumer<UITheme> listener : this.listeners)
        {
            try
            {
                listener.accept(newTheme);
            }
            catch (Exception e)
            {
                System.err.println("[BBS AI] 主题监听器异常：" + e.getMessage());
            }
        }

        /* 发布主题变更事件（供 addon 扩展） */
        if (notify)
        {
            BBSMod.events.post(new mchorse.bbs_ai.integration.event.ThemeChangeEvent(newTheme, oldTheme));
        }
    }

    /**
     * 创建主题对应的布局方案
     */
    private IUILayout createLayout(UITheme theme)
    {
        switch (theme)
        {
            case BLENDER_DARK: return new mchorse.bbs_ai.ui.theme.layouts.BlenderDarkLayout();
            case MINEIMATOR: return new mchorse.bbs_ai.ui.theme.layouts.MineimatorLayout();
            default: return new mchorse.bbs_ai.ui.theme.layouts.ClassicBBSLayout();
        }
    }

    /**
     * 获取当前主题下某语义色的最终色值（供自定义绘制使用）
     *
     * @param role 语义角色（main_background / panel_background / highlight / text / border / selected / warning）
     */
    public int color(String role)
    {
        return this.resourcePack.getColor(role, UITheme.CLASSIC_BBS == this.theme ? 0x00000000 : defaultRoleColor(role));
    }

    /**
     * 内置默认语义色（Blender / Mine-imator 主题缺少 colors.json 时的兜底）
     */
    public static int defaultRoleColor(String role)
    {
        switch (role)
        {
            case "main_background": return 0xff232323;
            case "panel_background": return 0xff2d2d2d;
            case "highlight": return 0xff4772a3;
            case "text": return 0xffe5e5e5;
            case "border": return 0xff101010;
            case "selected": return 0xff5680c2;
            case "warning": return 0xffe5b13a;
            default: return Colors.WHITE;
        }
    }

    /**
     * 主题目录 {@code config/bbs/ai_themes/}
     */
    public static File getThemesFolder()
    {
        return BBSMod.getGamePath("config/bbs/ai_themes");
    }
}
