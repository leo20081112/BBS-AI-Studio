package mchorse.bbs_ai.ui.language;

/**
 * UI 语言枚举（数据模型，主源集）
 *
 * <ul>
 *   <li>{@link #ENGLISH} —— English (en_us)</li>
 *   <li>{@link #CHINESE_SIMPLIFIED} —— 简体中文 (zh_cn)</li>
 *   <li>{@link #CHINESE_TRADITIONAL} —— 繁體中文 (zh_tw)</li>
 * </ul>
 *
 * <p>作者：BBS AI Studio</p>
 */
public enum UILanguage
{
    ENGLISH("en_us", "English"),
    CHINESE_SIMPLIFIED("zh_cn", "简体中文"),
    CHINESE_TRADITIONAL("zh_tw", "繁體中文");

    /**
     * 语言代码（与语言文件名一致）
     */
    public final String code;

    /**
     * 展示名
     */
    public final String title;

    UILanguage(String code, String title)
    {
        this.code = code;
        this.title = title;
    }

    /**
     * 由设置索引解析语言
     */
    public static UILanguage byIndex(int index)
    {
        UILanguage[] values = values();

        return values[Math.max(0, Math.min(values.length - 1, index))];
    }

    /**
     * 是否为中文（CJK 宽字符 UI 适配开关）
     */
    public boolean isChinese()
    {
        return this == CHINESE_SIMPLIFIED || this == CHINESE_TRADITIONAL;
    }
}
