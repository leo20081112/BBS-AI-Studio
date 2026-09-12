package mchorse.bbs_ai.import_manager;

import mchorse.bbs_ai.BBSAIStudio;
import mchorse.bbs_ai.format.MotionData;

import java.io.File;

/**
 * 导入条目数据类
 *
 * <p>描述一个可导入预览系统的动作数据文件：文件信息、元信息、来源类型。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ImportEntry
{
    /**
     * 来源类型
     */
    public final SourceType source;

    /**
     * 动作数据文件
     */
    public final File file;

    /**
     * 展示名（去除扩展名的文件名）
     */
    public final String name;

    /**
     * 帧数（解析失败时为 -1）
     */
    public int frames = -1;

    /**
     * 时长（秒，解析失败时为 -1）
     */
    public float duration = -1.0F;

    /**
     * 生成时间（ISO 8601，缺失时为空）
     */
    public String generatedAt = "";

    /**
     * 使用的模型（缺失时为空）
     */
    public String model = "";

    /**
     * 文件最后修改时间（毫秒）
     */
    public long lastModified;

    public ImportEntry(SourceType source, File file)
    {
        this.source = source;
        this.file = file;
        this.name = stripExtension(file.getName());
        this.lastModified = file.isFile() ? file.lastModified() : 0L;

        this.readMetadata();
    }

    /**
     * 尝试解析元信息（失败不致命，条目仍可展示）
     */
    private void readMetadata()
    {
        try
        {
            MotionData data = MotionData.readFromFile(this.file);

            this.frames = data.metadata.totalFrames > 0 ? data.metadata.totalFrames : data.keyframes.size();

            if (data.metadata.fps > 0)
            {
                this.duration = data.metadata.totalFrames > 0
                    ? data.metadata.totalFrames / (float) data.metadata.fps
                    : this.estimateDuration(data);
            }

            this.generatedAt = data.metadata.generatedAt;
            this.model = data.metadata.model;
        }
        catch (Exception e)
        {
            /* JSON 损坏或结构不符：保留默认 -1 值，由 UI 显示为未知 */
        }
    }

    /**
     * 依据关键帧 tick 估算时长（秒）
     */
    private float estimateDuration(MotionData data)
    {
        if (data.keyframes.isEmpty())
        {
            return -1.0F;
        }

        int lastTick = data.keyframes.get(data.keyframes.size() - 1).tick;

        return lastTick / 20.0F;
    }

    /**
     * 重新加载元信息（文件可能已被外部工具更新）
     */
    public void refresh()
    {
        this.lastModified = this.file.isFile() ? this.file.lastModified() : 0L;

        this.readMetadata();
    }

    /**
     * 动作数据是否与来源目录匹配（校验用）
     */
    public boolean isInternal()
    {
        return this.source == SourceType.INTERNAL_AI;
    }

    /**
     * 所属目录
     */
    public File getParentFolder()
    {
        return this.isInternal() ? BBSAIStudio.getAICacheFolder() : BBSAIStudio.getImportsFolder();
    }

    /**
     * 去除文件扩展名
     */
    private static String stripExtension(String name)
    {
        int dot = name.lastIndexOf('.');

        return dot > 0 ? name.substring(0, dot) : name;
    }
}
