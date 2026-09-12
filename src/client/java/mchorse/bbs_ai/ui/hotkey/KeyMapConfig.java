package mchorse.bbs_ai.ui.hotkey;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.File;
import java.io.IOException;

/**
 * 热键映射配置（持久化）
 *
 * <p>把用户自定义的热键保存到 {@code config/bbs/settings/bbs_ai_hotkeys.json}：
 * <pre>{@code
 * {
 *   "format": "bbs_ai_hotkeys_v1",
 *   "hotkeys": [
 *     { "id": "transform.grab", "key": 71, "mods": 0 }
 *   ]
 * }
 * }</pre></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class KeyMapConfig
{
    /**
     * 配置格式标识
     */
    public static final String FORMAT = "bbs_ai_hotkeys_v1";

    /**
     * 配置文件
     */
    private static final File FILE = new File(BBSMod.getSettingsFolder(), "bbs_ai_hotkeys.json");

    private KeyMapConfig()
    {}

    /**
     * 保存全部已修改的热键
     */
    public static boolean save()
    {
        try
        {
            MapType root = new MapType();

            root.putString("format", FORMAT);

            ListType list = new ListType();

            for (HotkeyDefinition definition : HotkeyRegistry.get().getAll())
            {
                if (!definition.isModified())
                {
                    continue;
                }

                MapType entry = new MapType();

                entry.putString("id", definition.id);
                entry.putInt("key", definition.key);
                entry.putInt("mods", definition.mods);
                list.add(entry);
            }

            root.put("hotkeys", list);

            return DataToString.writeSilently(FILE, root, true);
        }
        catch (Exception e)
        {
            System.err.println("[BBS AI] 保存热键配置失败：" + e.getMessage());

            return false;
        }
    }

    /**
     * 从磁盘恢复用户自定义热键（文件缺失或损坏时静默忽略）
     */
    public static void load(HotkeyRegistry registry)
    {
        if (!FILE.isFile())
        {
            return;
        }

        try
        {
            BaseType raw = DataToString.read(FILE);

            if (!(raw instanceof MapType))
            {
                return;
            }

            MapType root = (MapType) raw;

            if (!FORMAT.equals(root.getString("format", "")))
            {
                return;
            }

            ListType list = root.getList("hotkeys");

            for (int i = 0; i < list.size(); i++)
            {
                MapType entry = list.getMap(i);
                HotkeyDefinition definition = registry.get(entry.getString("id", ""));

                if (definition != null)
                {
                    definition.key = entry.getInt("key", definition.defaultKey);
                    definition.mods = entry.getInt("mods", definition.defaultMods);
                }
            }
        }
        catch (IOException e)
        {
            System.err.println("[BBS AI] 读取热键配置失败：" + e.getMessage());
        }
    }

    /**
     * 读取原始配置文本（调试用）
     */
    public static String readRaw()
    {
        try
        {
            return IOUtils.readText(FILE);
        }
        catch (Exception e)
        {
            return "";
        }
    }
}
