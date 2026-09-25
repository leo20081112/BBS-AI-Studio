package mchorse.bbs_ai.core;

import mchorse.bbs_ai.format.MotionFrame;
import mchorse.bbs_ai.import_manager.SourceType;

import java.util.List;

/**
 * AI 生成结果 → 预览系统接口契约
 *
 * <p>项目规范定义的核心数据流接口：AI 生成完成后由生成器调用，
 * 把关键帧送入预览系统（实现方通常直接调 {@code PreviewSystem.stage}）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public interface AIGenerationSink
{
    /**
     * AI 生成完成回调
     *
     * @param target  目标描述（影片 / 角色 / 时间范围）
     * @param frames  生成的关键帧
     * @param source  数据来源
     */
    void onGenerationComplete(mchorse.bbs_ai.preview.PreviewContext target, List<MotionFrame> frames, SourceType source);
}
