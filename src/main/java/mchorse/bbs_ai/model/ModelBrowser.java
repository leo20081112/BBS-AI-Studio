package mchorse.bbs_ai.model;

import mchorse.bbs_ai.BBSAIStudio;
import mchorse.bbs_mod.BBSMod;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 模型浏览器目录扫描
 *
 * <p>扫描三个目录汇总可导入模型（按修改时间倒序）：
 * <ul>
 *   <li>{@code config/bbs/models/} —— 游戏内导出的 .bbsm</li>
 *   <li>{@code config/bbs/imports/models/} —— 外部工具投放的 .bbsm / .bbs.json / .bobj</li>
 *   <li>{@code config/bbs/assets/models/} —— 游戏内模型目录中的 .bbsm（导入再分发用）</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ModelBrowser
{
    /**
     * 扫描全部模型来源目录
     */
    public static List<ModelEntry> scan()
    {
        List<ModelEntry> entries = new ArrayList<>();

        scanFolder(BBSAIStudio.getModelExportFolder(), ModelEntry.Source.EXPORT, entries);
        scanFolder(BBSAIStudio.getModelImportFolder(), ModelEntry.Source.IMPORT, entries);
        scanFolder(new File(BBSMod.getAssetsFolder(), "models"), ModelEntry.Source.ASSETS, entries);

        entries.sort(Comparator.comparingLong((ModelEntry entry) -> entry.modifiedAt).reversed());

        return entries;
    }

    /**
     * 按关键字过滤（名称 / 作者 / 格式，不区分大小写）
     */
    public static List<ModelEntry> filter(List<ModelEntry> entries, String keyword)
    {
        if (keyword == null || keyword.trim().isEmpty())
        {
            return entries;
        }

        String key = keyword.trim().toLowerCase(Locale.ROOT);
        List<ModelEntry> filtered = new ArrayList<>();

        for (ModelEntry entry : entries)
        {
            if (entry.name.toLowerCase(Locale.ROOT).contains(key)
                || entry.author.toLowerCase(Locale.ROOT).contains(key)
                || entry.format.contains(key))
            {
                filtered.add(entry);
            }
        }

        return filtered;
    }

    private static void scanFolder(File folder, ModelEntry.Source source, List<ModelEntry> entries)
    {
        if (!folder.isDirectory())
        {
            return;
        }

        File[] files = folder.listFiles((dir, name) ->
        {
            String lower = name.toLowerCase(Locale.ROOT);

            return lower.endsWith(".bbsm") || lower.endsWith(".bbs.json") || lower.endsWith(".bobj");
        });

        if (files == null)
        {
            return;
        }

        for (File file : files)
        {
            if (file.isFile() && file.length() > 0)
            {
                entries.add(new ModelEntry(source, file));
            }
        }
    }
}
