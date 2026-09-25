package mchorse.bbs_ai.model;

import java.io.File;
import java.util.Locale;

/**
 * 模型浏览器条目：一个可导入的模型文件
 *
 * <p>来源标记：游戏内导出目录 / 外部导入目录 / assets 模型目录。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ModelEntry
{
    /**
     * 条目来源
     */
    public enum Source
    {
        EXPORT("导出", "[导出]"),
        IMPORT("外部", "[外部]"),
        ASSETS("游戏内", "[游戏]");

        public final String title;
        public final String badge;

        Source(String title, String badge)
        {
            this.title = title;
            this.badge = badge;
        }
    }

    /**
     * 来源目录
     */
    public final Source source;

    /**
     * 模型文件
     */
    public final File file;

    /**
     * 显示名（.bbsm 取元数据，其余取文件名）
     */
    public String name = "";

    /**
     * 作者（仅 .bbsm 有）
     */
    public String author = "";

    /**
     * 描述（仅 .bbsm 有）
     */
    public String description = "";

    /**
     * 格式标签（bbsm / bbs.json / bobj）
     */
    public final String format;

    /**
     * 文件修改时间戳
     */
    public final long modifiedAt;

    /**
     * 几何统计（仅 .bbsm 有；-1 未知）
     */
    public int meshes = -1;
    public int bones = -1;

    public ModelEntry(Source source, File file)
    {
        this.source = source;
        this.file = file;
        this.modifiedAt = file.lastModified();

        String fileName = file.getName().toLowerCase(Locale.ROOT);

        if (fileName.endsWith(".bbsm"))
        {
            this.format = "bbsm";
            this.name = fileName.substring(0, fileName.length() - 5);
        }
        else if (fileName.endsWith(".bbs.json"))
        {
            this.format = "bbs.json";
            this.name = fileName.substring(0, fileName.length() - 9);
        }
        else if (fileName.endsWith(".bobj"))
        {
            this.format = "bobj";
            this.name = fileName.substring(0, fileName.length() - 5);
        }
        else
        {
            this.format = fileName;
            this.name = fileName;
        }

        this.refresh();
    }

    /**
     * 读取 .bbsm 元数据刷新显示字段（解析失败保持文件名显示）
     */
    public void refresh()
    {
        if (!"bbsm".equals(this.format))
        {
            return;
        }

        try
        {
            BBSSModel model = BBSSModel.readFromFile(this.file);

            if (!model.name.isEmpty())
            {
                this.name = model.name;
            }

            this.author = model.author;
            this.description = model.description;

            if (model.geometry != null && model.geometry.has("meshes"))
            {
                this.meshes = model.geometry.getAsJsonArray("meshes").size();
            }

            if (model.skeleton != null && model.skeleton.has("bones"))
            {
                this.bones = model.skeleton.getAsJsonArray("bones").size();
            }
        }
        catch (Exception ignored)
        {}
    }

    /**
     * 列表显示文本
     */
    public String toListLabel()
    {
        StringBuilder builder = new StringBuilder(this.source.badge + " " + this.name + " ." + this.format);

        if (!this.author.isEmpty())
        {
            builder.append(" — ").append(this.author);
        }

        if (this.meshes >= 0)
        {
            builder.append("（").append(this.meshes).append(" 网格");

            if (this.bones >= 0)
            {
                builder.append(" / ").append(this.bones).append(" 骨骼");
            }

            builder.append("）");
        }

        return builder.toString();
    }

    /**
     * 详情显示文本
     */
    public String toDetailLabel()
    {
        String detail = this.source.title + " | " + this.file.getParent();

        if (!this.description.isEmpty())
        {
            detail += " | " + (this.description.length() > 40 ? this.description.substring(0, 40) + "…" : this.description);
        }

        return detail;
    }
}
