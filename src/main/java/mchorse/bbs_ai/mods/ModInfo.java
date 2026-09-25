package mchorse.bbs_ai.mods;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 单个 mod 的识别结果
 *
 * <p>由 {@link ModCompatScanner} 扫描 Fabric Loader 元数据 + 注册表统计产生；
 * 可选叠加 {@link ModKnowledgeBase} 的内置知识卡片与适配器的动态补充，
 * 三者合并出「AI 视角的一条 mod 描述」。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ModInfo
{
    /**
     * modid（如 create）
     */
    public final String id;

    /**
     * 人类可读名称
     */
    public String name = "";

    /**
     * 版本
     */
    public String version = "";

    /**
     * 作者列表（合并为逗号分隔）
     */
    public String authors = "";

    /**
     * 描述原文（可能很长，digest 时截断）
     */
    public String description = "";

    /**
     * 自动推断的分类
     */
    public ModCategory category = ModCategory.UNKNOWN;

    /**
     * 内置知识库命中（null = 未收录）
     */
    public ModKnowledgeBase.Knowledge knowledge;

    /**
     * 注册表统计：该命名空间新增的物品 / 方块 / 实体数量（懒加载，可能为 -1 表示未统计）
     */
    public int items = -1;
    public int blocks = -1;
    public int entities = -1;

    /**
     * 该命名空间的实体 ID 样例（如 create:contraption，最多保留 16 个，供选角参考）
     */
    public final List<String> entitySamples = new ArrayList<>();

    /**
     * 适配器补充的 AI 说明行（如「可用于演员骨骼动画」）
     */
    public final List<String> adapterNotes = new ArrayList<>();

    public ModInfo(String id)
    {
        this.id = id;
    }

    /**
     * 是否被内置知识库收录（收录 = 主流 mod，digest 中优先展示）
     */
    public boolean isKnown()
    {
        return this.knowledge != null;
    }

    /**
     * 是否给游戏添加了可拍摄内容
     */
    public boolean hasContent()
    {
        return this.items > 0 || this.blocks > 0 || this.entities > 0;
    }

    /**
     * 生成列表展示用单行标签
     */
    public String toListLabel()
    {
        StringBuilder builder = new StringBuilder(this.name.isEmpty() ? this.id : this.name);

        builder.append(" [").append(this.id).append("]");

        if (this.entities > 0)
        {
            builder.append(" · ").append(this.entities).append("实体");
        }

        if (this.items > 0)
        {
            builder.append(" · ").append(this.items).append("物品");
        }

        if (this.knowledge != null)
        {
            builder.append(" · 已识别");
        }

        return builder.toString();
    }

    /**
     * 序列化为 JSON（mods_report.json 中的一个条目，外部工具链也可读取）
     */
    public JsonObject toJson()
    {
        JsonObject object = new JsonObject();

        object.addProperty("id", this.id);
        object.addProperty("name", this.name);
        object.addProperty("version", this.version);
        object.addProperty("authors", this.authors);
        object.addProperty("category", this.category.name());
        object.addProperty("known", this.knowledge != null);

        if (this.description != null && !this.description.isEmpty())
        {
            String trimmed = this.description.replaceAll("\\s+", " ").trim();

            object.addProperty("description", trimmed.length() > 200 ? trimmed.substring(0, 200) + "…" : trimmed);
        }

        if (this.knowledge != null)
        {
            object.addProperty("summary", this.knowledge.summary);
            object.addProperty("aiHint", this.knowledge.aiHint);

            JsonArray capabilities = new JsonArray();

            for (String capability : this.knowledge.capabilities)
            {
                capabilities.add(capability);
            }

            object.add("capabilities", capabilities);
        }

        JsonObject counts = new JsonObject();

        counts.addProperty("items", this.items);
        counts.addProperty("blocks", this.blocks);
        counts.addProperty("entities", this.entities);
        object.add("counts", counts);

        if (!this.entitySamples.isEmpty())
        {
            JsonArray samples = new JsonArray();

            for (String sample : this.entitySamples)
            {
                samples.add(sample);
            }

            object.add("entitySamples", samples);
        }

        if (!this.adapterNotes.isEmpty())
        {
            JsonArray notes = new JsonArray();

            for (String note : this.adapterNotes)
            {
                notes.add(note);
            }

            object.add("adapterNotes", notes);
        }

        return object;
    }
}
