package mchorse.bbs_ai.ui.theme;

import mchorse.bbs_ai.ui.theme.layouts.IUILayout;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.utils.colors.Colors;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        /* 应用主题色板到上游全局设置：secondaryColor 驱动整套表面阶梯
         * （Okllab 六级，自动翻转明暗文字），primaryColor 驱动强调色——
         * 这是让「切换主题看得出来」的关键（此前色板加载了却无消费者） */
        this.applyPalette(newTheme);

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
     * 应用主题色板到上游全局颜色设置（即时生效 + 随设置持久化）
     *
     * <p>Classic BBS = BBS 出厂默认；Blender 深色 = 中性深灰 + Blender 蓝；
     * Mine-imator = 浅色表面（lightSurfaces 自动翻转图标/文字为深色）+ 亮紫强调。</p>
     */
    private void applyPalette(UITheme theme)
    {
        int secondary;
        int primary;

        switch (theme)
        {
            case BLENDER_DARK:
                secondary = 0xff2d2d2d;
                primary = 0xff4772a3;
                break;
            case MINEIMATOR:
                secondary = 0xffe8eaed;
                primary = 0xff8a5cf6;
                break;
            default:
                secondary = 0xff171b22;
                primary = 0xffff3242;
                break;
        }

        if (mchorse.bbs_mod.BBSSettings.secondaryColor != null)
        {
            mchorse.bbs_mod.BBSSettings.secondaryColor.set(secondary);
        }

        if (mchorse.bbs_mod.BBSSettings.primaryColor != null)
        {
            mchorse.bbs_mod.BBSSettings.primaryColor.set(primary);
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
     * <p>回退链：config colors.json → 当前主题内置色板 → 白色。</p>
     *
     * @param role 语义角色（panel_background / card_background / code_background / highlight /
     *             text / text_muted / border / selected / warning / accent_text）
     */
    public int color(String role)
    {
        int fromPack = this.resourcePack.getColor(role, -1);

        if (fromPack != -1)
        {
            return fromPack;
        }

        String key = this.theme.id;
        Map<String, Integer> palette = THEME_PALETTES.getOrDefault(key, THEME_PALETTES.get(UITheme.CLASSIC_BBS.id));

        return palette.getOrDefault(role, Colors.WHITE);
    }

    /**
     * 每主题语义色默认表（AI 自绘组件消费；Mine-imator 为浅色主题，
     * 文字自动换深色由上游 lightSurfaces 机制处理，自绘部分走本表）
     */
    private static final Map<String, Map<String, Integer>> THEME_PALETTES = buildPalettes();

    private static Map<String, Map<String, Integer>> buildPalettes()
    {
        Map<String, Map<String, Integer>> palettes = new HashMap<>();

        Map<String, Integer> classic = new HashMap<>();

        classic.put("panel_background", 0xf01b1f27);
        classic.put("card_background", 0x30222428);
        classic.put("code_background", 0xf2080a10);
        classic.put("highlight", 0xffff3242);
        classic.put("text", 0xffd8dce4);
        classic.put("text_muted", 0xff9aa3b2);
        classic.put("border", 0xff2a2e3a);
        classic.put("selected", 0xff5680c2);
        classic.put("warning", 0xffe5b13a);
        classic.put("accent_text", 0xffff8a8a);
        palettes.put(UITheme.CLASSIC_BBS.id, classic);

        Map<String, Integer> blender = new HashMap<>();

        blender.put("panel_background", 0xf01c2530);
        blender.put("card_background", 0x33202836);
        blender.put("code_background", 0xf0151a24);
        blender.put("highlight", 0xff4772a3);
        blender.put("text", 0xffd5dde8);
        blender.put("text_muted", 0xff8fa0b8);
        blender.put("border", 0xff3d4656);
        blender.put("selected", 0xff5680c2);
        blender.put("warning", 0xffe5b13a);
        blender.put("accent_text", 0xff8ab4e0);
        palettes.put(UITheme.BLENDER_DARK.id, blender);

        Map<String, Integer> mineimator = new HashMap<>();

        mineimator.put("panel_background", 0xf0eef1f6);
        mineimator.put("card_background", 0x50ffffff);
        mineimator.put("code_background", 0xf0e3e8f2);
        mineimator.put("highlight", 0xff8a5cf6);
        mineimator.put("text", 0xff2a2e38);
        mineimator.put("text_muted", 0xff6a7285);
        mineimator.put("border", 0xffc9cedd);
        mineimator.put("selected", 0xff8a5cf6);
        mineimator.put("warning", 0xffb7791f);
        mineimator.put("accent_text", 0xff5a3fb0);
        palettes.put(UITheme.MINEIMATOR.id, mineimator);

        return palettes;
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
