package mchorse.bbs_ai.ui.theme;

import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 主题资源包
 *
 * <p>动态加载主题的颜色（colors.json）与布局参数（layout.json）。
 * 加载顺序：内置默认值 ← {@code config/bbs/ai_themes/<主题id>/} 覆盖。</p>
 *
 * <p>colors.json 示例：
 * <pre>{@code
 * {
 *   "main_background": "#232323",
 *   "panel_background": "#2d2d2d",
 *   "highlight": "#4772a3",
 *   "text": "#e5e5e5",
 *   "border": "#101010",
 *   "selected": "#5680c2",
 *   "warning": "#e5b13a"
 * }
 * }</pre></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ThemeResourcePack
{
    /**
     * 语义色表（角色名 → ARGB）
     */
    private final Map<String, Integer> colors = new HashMap<>();

    /**
     * 布局参数表（参数名 → 数值）
     */
    private final Map<String, Float> layout = new HashMap<>();

    /**
     * 是否成功加载过资源
     */
    private boolean loaded;

    /**
     * 当前加载的主题 ID（提取内置模板用）
     */
    private String currentThemeId = "";

    /**
     * 加载指定主题的资源
     */
    public void load(mchorse.bbs_ai.ui.theme.UITheme theme)
    {
        this.loaded = false;
        this.colors.clear();
        this.layout.clear();
        this.currentThemeId = theme.id;

        File folder = new File(ThemeManager.getThemesFolder(), theme.id);

        this.loadColors(new File(folder, "colors.json"));
        this.loadLayout(new File(folder, "layout.json"));

        this.loaded = true;
    }

    /**
     * 是否加载过
     */
    public boolean isLoaded()
    {
        return this.loaded;
    }

    /**
     * 读取颜色
     */
    public int getColor(String role, int fallback)
    {
        return this.colors.getOrDefault(role, fallback);
    }

    /**
     * 读取布局浮点参数
     */
    public float getLayoutValue(String key, float fallback)
    {
        return this.layout.getOrDefault(key, fallback);
    }

    /**
     * 加载 colors.json（优先 config 目录，缺失时回退 jar 内置模板）
     */
    private void loadColors(File file)
    {
        if (!file.isFile())
        {
            file = extractBundled("ai_themes/" + this.currentThemeId + "/colors.json", file);

            if (file == null || !file.isFile())
            {
                return;
            }
        }

        try
        {
            BaseType raw = DataToString.read(file);

            if (raw instanceof MapType)
            {
                for (String key : ((MapType) raw).keys())
                {
                    String value = ((MapType) raw).getString(key, "");

                    int color = this.parseColor(value);

                    if (color != -1)
                    {
                        this.colors.put(key, color);
                    }
                }
            }
        }
        catch (IOException e)
        {
            System.err.println("[BBS AI] 读取主题颜色失败：" + file + "，" + e.getMessage());
        }
    }

    /**
     * 加载 layout.json（优先 config 目录，缺失时回退 jar 内置模板）
     */
    private void loadLayout(File file)
    {
        if (!file.isFile())
        {
            file = extractBundled("ai_themes/" + this.currentThemeId + "/layout.json", file);

            if (file == null || !file.isFile())
            {
                return;
            }
        }

        try
        {
            BaseType raw = DataToString.read(file);

            if (raw instanceof MapType)
            {
                for (String key : ((MapType) raw).keys())
                {
                    this.layout.put(key, (float) ((MapType) raw).getDouble(key));
                }
            }
        }
        catch (IOException e)
        {
            System.err.println("[BBS AI] 读取主题布局失败：" + file + "，" + e.getMessage());
        }
    }

    /**
     * 从 jar 内置资源提取主题模板到 config 目录（首次运行时）
     *
     * @return 提取后的文件；无内置资源时为 null
     */
    private File extractBundled(String path, File destination)
    {
        try (java.io.InputStream stream = mchorse.bbs_mod.BBSMod.getProvider().getAsset(mchorse.bbs_mod.resources.Link.assets(path)))
        {
            if (stream == null)
            {
                return null;
            }

            destination.getParentFile().mkdirs();
            mchorse.bbs_mod.utils.IOUtils.writeText(destination, mchorse.bbs_mod.utils.IOUtils.readText(stream));

            return destination;
        }
        catch (Exception e)
        {
            /* 无内置模板（如 classic 主题），静默忽略 */
            return null;
        }
    }

    /**
     * 解析 "#rrggbb" / "#aarrggbb" / "0x..." 十六进制颜色；不合法返回 -1
     */
    private int parseColor(String value)
    {
        if (value == null || value.isEmpty())
        {
            return -1;
        }

        String hex = value.startsWith("#") ? value.substring(1) : value.startsWith("0x") ? value.substring(2) : value;

        try
        {
            int parsed = (int) Long.parseLong(hex, 16);

            /* 6 位十六进制视为不透明 RGB */
            if (hex.length() == 6)
            {
                return parsed | 0xFF000000;
            }

            return parsed;
        }
        catch (NumberFormatException e)
        {
            return -1;
        }
    }
}
