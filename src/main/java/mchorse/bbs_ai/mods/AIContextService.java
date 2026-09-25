package mchorse.bbs_ai.mods;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * AI 上下文服务
 *
 * <p>把 Mod 兼容扫描结果组装成「喂给大模型的环境摘要」，供分镜生成、
 * AI 编辑器对话等所有 AI 功能共享 —— 这是「让 AI 理解插件内容」的统一出口。</p>
 *
 * <p>第三方 addon 可通过 {@link #registerExtraContext} 注入自己的上下文片段
 * （例如为自己的 mod 补充详细能力说明）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class AIContextService
{
    /**
     * 单例
     */
    private static final AIContextService INSTANCE = new AIContextService();

    /**
     * addon 注入的额外上下文提供者
     */
    private final List<Supplier<String>> extraContexts = new ArrayList<>();

    /**
     * 缓存的 mod digest（rescan 后失效）
     */
    private String cachedDigest;

    /**
     * digest 构建时的 mod 数量（用于检测环境变化）
     */
    private int cachedModCount = -1;

    /**
     * 是否把 mod 环境注入 AI 提示词（AI 设置 → 插件调用）
     */
    private boolean modContextEnabled = true;

    private AIContextService()
    {}

    public static AIContextService get()
    {
        return INSTANCE;
    }

    /**
     * 设置是否注入 mod 环境上下文
     */
    public void setModContextEnabled(boolean enabled)
    {
        this.modContextEnabled = enabled;
    }

    public boolean isModContextEnabled()
    {
        return this.modContextEnabled;
    }

    /**
     * addon 注册额外上下文片段（每次构建 digest 时调用）
     */
    public void registerExtraContext(Supplier<String> context)
    {
        if (context != null)
        {
            this.extraContexts.add(context);
        }
    }

    /**
     * 构建 mod 环境 digest（带缓存）
     *
     * @param detailed true = 附带实体样例与未收录 mod 明细（AI 编辑器长对话用）；
     *                 false = 只保留分类汇总与已识别 mod（分镜提示词用，省 token）
     */
    public String buildModDigest(boolean detailed)
    {
        if (!this.modContextEnabled)
        {
            return "（mod 环境注入已在 AI 设置中关闭）";
        }

        ModCompatScanner scanner = ModCompatScanner.get();
        List<ModInfo> mods = scanner.getMods();

        if (this.cachedDigest != null && this.cachedModCount == mods.size())
        {
            return this.cachedDigest;
        }

        StringBuilder builder = new StringBuilder();

        builder.append("## 当前游戏 mod 环境（自动识别）\n");
        builder.append("共 ").append(mods.size()).append(" 个 mod，")
            .append(scanner.count(ModInfo::isKnown)).append(" 个已被内置知识库识别。\n\n");

        /* 分类汇总 */
        Map<ModCategory, Integer> byCategory = new EnumMap<>(ModCategory.class);

        for (ModInfo info : mods)
        {
            byCategory.merge(info.category, 1, Integer::sum);
        }

        builder.append("### 分类汇总\n");

        for (Map.Entry<ModCategory, Integer> entry : byCategory.entrySet())
        {
            builder.append("- ").append(entry.getKey().title).append("：").append(entry.getValue()).append(" 个\n");
        }

        /* 已识别的主流 mod（带拍摄提示） */
        List<ModInfo> known = new ArrayList<>();

        for (ModInfo info : mods)
        {
            if (info.isKnown())
            {
                known.add(info);
            }
        }

        if (!known.isEmpty())
        {
            builder.append("\n### 重点 mod（含拍摄建议）\n");

            for (ModInfo info : known)
            {
                ModKnowledgeBase.Knowledge knowledge = info.knowledge;

                builder.append("- **").append(knowledge.name).append("**（").append(info.id).append("）：")
                    .append(knowledge.summary).append(" 拍摄建议：").append(knowledge.aiHint).append("\n");
            }
        }

        /* 实体资源（选角关键信息） */
        List<ModInfo> withEntities = new ArrayList<>();

        for (ModInfo info : mods)
        {
            if (info.entities > 0)
            {
                withEntities.add(info);
            }
        }

        if (!withEntities.isEmpty())
        {
            builder.append("\n### 可用演员（新增实体的 mod，实体可作为 BBS 角色形态）\n");

            for (ModInfo info : withEntities)
            {
                builder.append("- ").append(info.id).append("：").append(info.entities).append(" 种实体");

                if (!info.entitySamples.isEmpty())
                {
                    builder.append("（样例：").append(String.join("、", info.entitySamples.subList(0, Math.min(4, info.entitySamples.size())))).append("…）");
                }

                builder.append("\n");
            }
        }

        if (detailed)
        {
            /* 未收录 mod 明细 */
            builder.append("\n### 其它已安装 mod\n");

            for (ModInfo info : mods)
            {
                if (info.isKnown())
                {
                    continue;
                }

                builder.append("- ").append(info.name.isEmpty() ? info.id : info.name)
                    .append("（").append(info.id).append("，").append(info.category.title).append("）");

                String description = info.description == null ? "" : info.description.replaceAll("\\s+", " ").trim();

                if (!description.isEmpty())
                {
                    builder.append("：").append(description.length() > 100 ? description.substring(0, 100) + "…" : description);
                }

                builder.append("\n");
            }
        }

        /* addon 注入的额外上下文 */
        for (Supplier<String> supplier : this.extraContexts)
        {
            try
            {
                String extra = supplier.get();

                if (extra != null && !extra.isEmpty())
                {
                    builder.append("\n").append(extra.trim()).append("\n");
                }
            }
            catch (Exception exception)
            {
                System.err.println("[BBS AI] 额外 AI 上下文提供者抛错（忽略）：" + exception);
            }
        }

        this.cachedDigest = builder.toString();
        this.cachedModCount = mods.size();

        return this.cachedDigest;
    }

    /**
     * 使缓存失效（rescan / addon 注册新上下文后调用）
     */
    public void invalidate()
    {
        this.cachedDigest = null;
        this.cachedModCount = -1;
    }
}
