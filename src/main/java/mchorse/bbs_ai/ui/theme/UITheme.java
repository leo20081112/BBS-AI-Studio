package mchorse.bbs_ai.ui.theme;

/**
 * UI 主题枚举（数据模型，主源集）
 *
 * <ul>
 *   <li>{@link #CLASSIC_BBS} —— 原版 BBS FS 风格（默认）</li>
 *   <li>{@link #BLENDER_DARK} —— Blender 深色主题</li>
 *   <li>{@link #MINEIMATOR} —— Mine-imator 风格（扁平化、大面积时间轴）</li>
 * </ul>
 *
 * <p>作者：BBS AI Studio</p>
 */
public enum UITheme
{
    CLASSIC_BBS("classic", "经典 BBS"),
    BLENDER_DARK("blender", "Blender 深色"),
    MINEIMATOR("mineimator", "Mine-imator");

    /**
     * 主题资源目录名（assets/bbs/textures/ui/theme_<id>/）
     */
    public final String id;

    /**
     * 中文展示名
     */
    public final String title;

    UITheme(String id, String title)
    {
        this.id = id;
        this.title = title;
    }

    /**
     * 由设置索引解析主题
     */
    public static UITheme byIndex(int index)
    {
        UITheme[] values = values();

        return values[Math.max(0, Math.min(values.length - 1, index))];
    }
}
