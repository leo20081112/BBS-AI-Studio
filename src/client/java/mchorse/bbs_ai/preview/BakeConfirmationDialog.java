package mchorse.bbs_ai.preview;

import java.util.List;

import mchorse.bbs_ai.format.MotionFrame;
import mchorse.bbs_ai.motion.SkeletonMapper;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;

/**
 * 烘焙确认对话框
 *
 * <p>确认把预览数据正式写入 Film 前的摘要展示（项目规范模块 7）：
 * <ul>
 *   <li>摘要信息：新增关键帧数、影响骨骼列表、时间范围、冲突数量</li>
 *   <li>操作按钮：「覆盖现有」「插入 Blend」「取消」</li>
 * </ul>
 * 确认后调用 {@link PreviewSystem#bake(BakeMode)}【原版兼容】。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BakeConfirmationDialog extends UIOverlayPanel
{
    public BakeConfirmationDialog(PreviewContext context, PreviewTrack track)
    {
        super(L10n.lang("bbs_ai.str.bakeDialog.1"));

        int frames = track.getFrameCount();
        List<String> bones = track.getAffectedBones();
        int rangeStart = track.getStartTick();
        int rangeEnd = track.getEndTick();
        int[] conflicts = PreviewSystem.get().detectConflicts(context);

        String[] lines = this.buildSummaryLines(frames, bones, rangeStart, rangeEnd, conflicts);

        UIButton overwrite = new UIButton(L10n.lang("bbs_ai.str.bakeDialog.2"), (b) ->
        {
            this.close();
            PreviewSystem.get().bake(BakeMode.OVERWRITE);
        });

        UIButton blend = new UIButton(L10n.lang("bbs_ai.str.bakeDialog.3"), (b) ->
        {
            this.close();
            PreviewSystem.get().bake(BakeMode.INSERT_BLEND);
        });

        UIButton cancel = new UIButton(L10n.lang("bbs_ai.str.bakeDialog.4"), (b) ->
        {
            this.close();
            PreviewSystem.get().discard();
        });

        int width = 130;

        overwrite.relative(this.content).x(10).y(1F, -30).w(width).h(20);
        blend.relative(this.content).x(0.5F, -width / 2).y(1F, -30).w(width).h(20);
        cancel.relative(this.content).x(1F, -10 - width).y(1F, -30).w(width).h(20);

        this.content.add(overwrite, blend, cancel);

        /* 逐行摘要标签（避开底部按钮区） */
        int y = 10;

        for (String line : lines)
        {
            UILabel label = new UILabel(IKey.constant(line));

            label.relative(this.content).xy(10, y).w(1F, -20);
            this.content.add(label);

            y += 14;
        }
    }

    /**
     * 构建摘要文本行
     */
    private String[] buildSummaryLines(int frames, List<String> bones, int rangeStart, int rangeEnd, int[] conflicts)
    {
        return new String[] {
            "新增关键帧：" + frames + " 帧",
            "影响骨骼：" + (bones.isEmpty() ? "（无）" : String.join("、", bones)),
            "时间范围：" + rangeStart + " ~ " + rangeEnd + " tick（" + String.format("%.1f", (rangeEnd - rangeStart) / 20.0F) + " 秒）",
            "涉及骨骼数：" + bones.size() + " / " + SkeletonMapper.ALL_BONES.length,
            conflicts[1] > 0
                ? "⚠ 冲突警告：与现有关键帧重叠 " + conflicts[1] + " 帧（" + conflicts[0] + " 个通道）"
                : "无时间冲突"
        };
    }
}
