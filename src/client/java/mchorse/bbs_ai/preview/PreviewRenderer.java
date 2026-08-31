package mchorse.bbs_ai.preview;

import java.util.List;

import mchorse.bbs_ai.core.BBSAISettings;
import mchorse.bbs_ai.format.MotionFrame;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * 预览渲染器（客户端）
 *
 * <p>负责预烘焙预览的可视化（在 BBS 的 2D UI 层渲染，避免侵入 3D 管线）：
 * <ul>
 *   <li>预览模式标记（左上角蓝色高亮边框 + "预览模式" 文本）</li>
 *   <li>洋葱皮指示条：时间轴附近的绿/红帧刻度（前帧绿、后帧红）</li>
 *   <li>冲突标记：与现有关键帧重叠的时间范围绘制黄色警告边框</li>
 *   <li>预览骨骼旋转采样（FloatBuffer 快速通道，供 3D 姿态预览宿主读取）</li>
 * </ul></p>
 *
 * <p>线程契约：render 在主线程调用；预览数据由后台线程通过
 * {@link PreviewSystem#stage} 写入后不再修改。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class PreviewRenderer
{
    /**
     * 读取指定帧序号、骨骼的预览旋转（度），供 3D 层使用
     *
     * @param key       预览缓存键（影片|角色|表单）
     * @param frameIndex 帧序号
     * @param boneIndex  骨骼序号（SkeletonMapper.ALL_BONES 顺序）
     * @param out        输出 [x, y, z]（度）
     * @return 是否成功读取
     */
    public boolean sampleRotation(String key, int frameIndex, int boneIndex, float[] out)
    {
        PreviewTrack track = PreviewSystem.get().getCache().get(key);

        if (track == null || frameIndex < 0 || frameIndex >= track.getFrameCount())
        {
            return false;
        }

        track.getRotation(frameIndex, boneIndex, out);

        return true;
    }

    /**
     * 获取预览轨道在给定 tick 附近的姿态（3D 预览宿主用）
     *
     * @param key 预览缓存键
     * @param tick 当前时间轴 tick
     * @return 最近的 MotionFrame；无预览时为 null
     */
    public MotionFrame getFrameAt(String key, float tick)
    {
        PreviewTrack track = PreviewSystem.get().getCache().get(key);

        if (track == null || track.getFrameCount() == 0)
        {
            return null;
        }

        MotionFrame best = null;
        float bestDistance = Float.MAX_VALUE;

        for (MotionFrame frame : track.getFrames())
        {
            float distance = Math.abs(frame.tick - tick);

            if (distance < bestDistance)
            {
                bestDistance = distance;
                best = frame;
            }
        }

        return best;
    }

    /**
     * 渲染预览 HUD 覆盖层（宿主面板 render 内调用，2D 层）
     *
     * @param context UI 上下文
     * @param area    面板区域
     * @param currentTick 当前时间轴 tick
     */
    public void renderOverlay(UIContext context, mchorse.bbs_mod.ui.utils.Area area, float currentTick)
    {
        PreviewSystem system = PreviewSystem.get();

        if (!system.isPreviewMode() || system.getCache().size() == 0)
        {
            return;
        }

        Batcher2D batcher = context.batcher;
        int onionFrames = BBSAISettings.previewOnionFrames.get();
        int nextColor = BBSAISettings.previewOnionNextColor.get();
        int prevColor = BBSAISettings.previewOnionPrevColor.get();
        int conflictColor = BBSAISettings.previewConflictColor.get();

        /* 预览模式高亮边框（蓝色） */
        int highlight = BBSAISettings.previewHighlightColor.get();

        batcher.box(area.x, area.y, area.ex(), area.y + 2, highlight);
        batcher.box(area.x, area.ey() - 2, area.ex(), area.ey(), highlight);
        batcher.box(area.x, area.y, area.x + 2, area.ey(), highlight);
        batcher.box(area.ex() - 2, area.y, area.ex(), area.ey(), highlight);

        /* 预览模式标签 */
        String label = "预览模式 | Ctrl+Enter 烘焙 | Ctrl+Esc 放弃";
        int labelWidth = batcher.getFont().getWidth(label);

        batcher.box(area.x + 6, area.y + 6, area.x + 6 + labelWidth + 8, area.y + 24, 0x88000000);
        batcher.text(label, area.x + 10, area.y + 10, Colors.WHITE);

        /* 洋葱皮指示条：对每条预览轨道绘制帧刻度点 */
        for (String key : system.getCache().keys())
        {
            PreviewTrack track = system.getCache().get(key);

            if (track == null)
            {
                continue;
            }

            List<MotionFrame> frames = track.getFrames();
            int total = Math.max(1, track.getEndTick() - track.getStartTick());
            int barY = area.ey() - 14;

            for (int i = 0; i < frames.size(); i++)
            {
                MotionFrame frame = frames.get(i);
                float ratio = (frame.tick - track.getStartTick()) / (float) total;
                int x = area.x + (int) (ratio * (area.w - 8)) + 4;

                /* 前后帧范围高亮 */
                int distance = (int) Math.abs(frame.tick - currentTick);

                if (distance == 0)
                {
                    batcher.box(x - 1, barY - 2, x + 2, barY + 6, Colors.WHITE | Colors.A100);
                }
                else if (frame.tick > currentTick && distance <= onionFrames)
                {
                    /* 前帧（未来）：绿色 */
                    batcher.box(x - 1, barY, x + 1, barY + 4, nextColor);
                }
                else if (frame.tick < currentTick && distance <= onionFrames)
                {
                    /* 后帧（过去）：红色 */
                    batcher.box(x - 1, barY, x + 1, barY + 4, prevColor);
                }
                else
                {
                    batcher.box(x - 1, barY + 1, x + 1, barY + 3, highlight);
                }
            }

            /* 冲突标记 */
            PreviewContext previewContext = system.getCache().getContext(key);

            if (previewContext != null)
            {
                int[] conflicts = system.detectConflicts(previewContext);

                if (conflicts[1] > 0)
                {
                    String warning = "冲突: " + conflicts[1] + " 帧";

                    batcher.box(area.ex() - batcher.getFont().getWidth(warning) - 14, area.y + 6, area.ex() - 6, area.y + 24, conflictColor);
                    batcher.text(warning, area.ex() - batcher.getFont().getWidth(warning) - 10, area.y + 10, Colors.A100);
                }
            }
        }
    }
}
