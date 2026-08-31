package mchorse.bbs_ai.preview;

import mchorse.bbs_ai.import_manager.SourceType;

/**
 * 预览上下文
 *
 * <p>描述一次预烘焙预览的目标与来源：写入哪个影片 / 哪个角色（Replay）、
 * 覆盖的时间范围、数据来源。每次 {@code PreviewSystem.stage()} 创建一份。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class PreviewContext
{
    /**
     * 目标影片 ID（FilmManager 键；预览期允许为空 = 尚未落盘的新影片）
     */
    public final String filmId;

    /**
     * 目标 Replay 的 ID（角色）
     */
    public final String replayId;

    /**
     * 目标表单路径（骨骼通道前缀，空串 = 主表单）
     */
    public final String formPath;

    /**
     * 数据来源
     */
    public final SourceType source;

    /**
     * 起始 tick
     */
    public final int tickStart;

    /**
     * 结束 tick
     */
    public final int tickEnd;

    /**
     * 创建时间戳（毫秒）
     */
    public final long createdAt;

    public PreviewContext(String filmId, String replayId, String formPath, SourceType source, int tickStart, int tickEnd)
    {
        this.filmId = filmId == null ? "" : filmId;
        this.replayId = replayId == null ? "0" : replayId;
        this.formPath = formPath == null ? "" : formPath;
        this.source = source == null ? SourceType.INTERNAL_AI : source;
        this.tickStart = tickStart;
        this.tickEnd = tickEnd;
        this.createdAt = System.currentTimeMillis();
    }

    /**
     * 缓存键：影片 + 角色 + 表单
     */
    public String cacheKey()
    {
        return this.filmId + "|" + this.replayId + "|" + this.formPath;
    }
}
