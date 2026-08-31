package mchorse.bbs_ai.import_manager;

/**
 * 导入条目来源类型
 *
 * <p>统一导入管理器用该枚举区分动作数据的来源：
 * <ul>
 *   <li>{@link #INTERNAL_AI} —— 游戏内 AI 生成（配置目录 {@code config/bbs/ai_cache/}）</li>
 *   <li>{@link #EXTERNAL_TOOL} —— 外部工具链导入（配置目录 {@code config/bbs/imports/}）</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public enum SourceType
{
    /**
     * 游戏内 AI 生成（🤖）
     */
    INTERNAL_AI("\uD83E\uDD16", "游戏内生成"),

    /**
     * 外部工具导入（📥）
     */
    EXTERNAL_TOOL("\uD83D\uDCE5", "外部导入");

    /**
     * 展示图标（Unicode 字符，UI 上直接绘制）
     */
    private final String icon;

    /**
     * 中文展示名
     */
    private final String title;

    SourceType(String icon, String title)
    {
        this.icon = icon;
        this.title = title;
    }

    /**
     * 展示图标字符
     */
    public String getIcon()
    {
        return this.icon;
    }

    /**
     * 中文展示名
     */
    public String getTitle()
    {
        return this.title;
    }
}
