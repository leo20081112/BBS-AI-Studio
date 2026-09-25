package mchorse.bbs_ai.mods;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/**
 * Mod 兼容扫描器（底层服务）
 *
 * <p>自动识别当前游戏安装的全部 mod 并让 AI「理解」它们：
 * <ol>
 *   <li>Fabric Loader 元数据（id / 名称 / 版本 / 作者 / 描述）</li>
 *   <li>{@link ModKnowledgeBase} 内置知识卡片（主流开源 mod 的拍摄视角知识）</li>
 *   <li>关键词自动分类（{@link ModCategory}），未收录 mod 也能归入大类</li>
 *   <li>注册表统计：每个 mod 新增的物品 / 方块 / 实体数量与实体 ID 样例（选角参考）</li>
 * </ol></p>
 *
 * <p>注册表统计在 mod 注册完成后才有意义，因此 {@link #collectRegistryStats()} 懒执行
 * （首次取 digest 或手动 rescan 时），并整体 try/catch 兜底 —— 统计失败不影响其它功能。
 * 扫描结果落盘 {@code config/bbs/ai/mods_report.json}，外部工具链与 AI 均可读取。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ModCompatScanner
{
    /**
     * 单例
     */
    private static ModCompatScanner instance;

    /**
     * 扫描结果（按名称排序）
     */
    private final List<ModInfo> mods = new ArrayList<>();

    /**
     * 注册表统计是否已完成
     */
    private boolean statsCollected;

    /**
     * 报告是否已导出（本次会话）
     */
    private boolean reportSaved;

    private ModCompatScanner()
    {}

    /**
     * 初始化（AICore 底层启动时调用）
     */
    public static synchronized void initialize()
    {
        if (instance == null)
        {
            instance = new ModCompatScanner();
            instance.scan();

            System.out.println("[BBS AI] Mod 兼容扫描完成：识别 " + instance.mods.size() + " 个 mod（"
                + instance.count(ModInfo::isKnown) + " 个命中内置知识库）");
        }
    }

    /**
     * 获取单例（未初始化则兜底初始化）
     */
    public static ModCompatScanner get()
    {
        if (instance == null)
        {
            initialize();
        }

        return instance;
    }

    /**
     * 全量重扫（客户端「重新扫描」按钮 / addon 触发）
     */
    public synchronized void rescan()
    {
        this.mods.clear();
        this.statsCollected = false;
        this.reportSaved = false;
        this.scan();
    }

    /**
     * 扫描 Fabric Loader 元数据 + 知识库 + 分类
     */
    private void scan()
    {
        for (ModContainer container : FabricLoader.getInstance().getAllMods())
        {
            ModMetadata metadata = container.getMetadata();
            String id = metadata.getId();

            /* 跳过minecraft本体与javadoc伪mod */
            if ("minecraft".equals(id) || "java".equals(id) || "fabricloader".equals(id))
            {
                continue;
            }

            ModInfo info = new ModInfo(id);

            info.name = safe(metadata.getName());
            info.version = safe(metadata.getVersion().getFriendlyString());
            info.description = safe(metadata.getDescription());

            StringBuilder authors = new StringBuilder();

            for (Person person : metadata.getAuthors())
            {
                if (authors.length() > 0)
                {
                    authors.append(", ");
                }

                authors.append(person.getName());
            }

            info.authors = authors.toString();

            /* 分类优先级：内置知识卡片 > 关键词推断 */
            info.knowledge = ModKnowledgeBase.lookup(id);
            info.category = info.knowledge != null ? info.knowledge.category : ModCategory.detect(id, info.name, info.description);

            this.mods.add(info);
        }

        this.mods.sort(Comparator.comparing((mod) -> mod.name.isEmpty() ? mod.id : mod.name, String::compareToIgnoreCase));
    }

    /**
     * 补齐注册表统计并应用 AI 兼容适配器（懒执行；任何失败都被吞掉并记日志）
     */
    public synchronized void collectRegistryStats()
    {
        if (this.statsCollected)
        {
            return;
        }

        this.statsCollected = true;

        try
        {
            for (ModInfo info : this.mods)
            {
                info.items = countNamespace(Registries.ITEM.getIds(), info.id);
                info.blocks = countNamespace(Registries.BLOCK.getIds(), info.id);
                info.entities = countNamespace(Registries.ENTITY_TYPE.getIds(), info.id);

                if (info.entities > 0)
                {
                    info.entitySamples.clear();
                    info.entitySamples.addAll(sampleNamespace(Registries.ENTITY_TYPE.getIds(), info.id, 16));
                }
            }
        }
        catch (Throwable throwable)
        {
            /* 注册表统计属于增强信息，任何环境差异都不应影响主流程 */
            System.err.println("[BBS AI] 注册表统计失败（忽略）：" + throwable);
        }

        /* 适配器在统计完成后应用（EntitySourceAdapter 等依赖实体样例） */
        mchorse.bbs_ai.compat.ModAdapterManager.applyAll(this, this.mods);
    }

    /**
     * 按命名空间统计注册项数量
     */
    private static int countNamespace(Iterable<Identifier> ids, String namespace)
    {
        int count = 0;

        for (Identifier id : ids)
        {
            if (id.getNamespace().equals(namespace))
            {
                count++;
            }
        }

        return count;
    }

    /**
     * 取命名空间下前 N 个注册项（保持注册顺序的稳定样例）
     */
    private static List<String> sampleNamespace(Iterable<Identifier> ids, String namespace, int limit)
    {
        List<String> samples = new ArrayList<>();

        for (Identifier id : ids)
        {
            if (id.getNamespace().equals(namespace))
            {
                samples.add(id.toString());

                if (samples.size() >= limit)
                {
                    break;
                }
            }
        }

        return samples;
    }

    /**
     * 获取扫描结果（保证注册表统计已补齐）
     */
    public synchronized List<ModInfo> getMods()
    {
        this.collectRegistryStats();

        return this.mods;
    }

    /**
     * 按 modid 查找
     */
    public synchronized ModInfo byId(String modId)
    {
        for (ModInfo info : this.mods)
        {
            if (info.id.equals(modId))
            {
                return info;
            }
        }

        return null;
    }

    /**
     * 统计满足条件的 mod 数量
     */
    public synchronized int count(java.util.function.Predicate<ModInfo> filter)
    {
        int count = 0;

        for (ModInfo info : this.mods)
        {
            if (filter.test(info))
            {
                count++;
            }
        }

        return count;
    }

    /**
     * 导出完整报告到 {@code config/bbs/ai/mods_report.json}（返回文件；失败返回 null）
     */
    public synchronized File saveReport()
    {
        try
        {
            File folder = mchorse.bbs_mod.BBSMod.getGamePath("config/bbs/ai");

            folder.mkdirs();

            File file = new File(folder, "mods_report.json");
            JsonObject root = new JsonObject();

            root.addProperty("format", "bbs_ai_mods_report_v1");
            root.addProperty("generatedAt", java.time.Instant.now().toString());
            root.addProperty("total", this.mods.size());
            root.addProperty("known", this.count(ModInfo::isKnown));

            JsonArray array = new JsonArray();

            for (ModInfo info : this.getMods())
            {
                array.add(info.toJson());
            }

            root.add("mods", array);

            try (PrintWriter writer = new PrintWriter(file, StandardCharsets.UTF_8))
            {
                writer.println(new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root));
            }

            this.reportSaved = true;

            return file;
        }
        catch (Exception exception)
        {
            System.err.println("[BBS AI] 导出 mod 报告失败：" + exception);

            return null;
        }
    }

    /**
     * 是否已导出过报告
     */
    public boolean isReportSaved()
    {
        return this.reportSaved;
    }

    /**
     * 环境概览单行（面板顶部展示）
     */
    public synchronized String summaryLine()
    {
        this.collectRegistryStats();

        return String.format(Locale.ROOT, "%d 个 mod · %d 已识别 · %d 新增实体 · %d 新增方块",
            this.mods.size(),
            this.count(ModInfo::isKnown),
            this.count((info) -> info.entities > 0),
            this.count((info) -> info.blocks > 0));
    }

    private static String safe(String text)
    {
        return text == null ? "" : text;
    }
}
