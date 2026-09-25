package mchorse.bbs_ai.ui.model;

import mchorse.bbs_ai.motion.SkeletonMapper;
import mchorse.bbs_ai.motion.SkeletonMapping;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 骨骼映射确认/编辑面板
 *
 * <p>把视频识别输出的标准六骨骼槽位（head / body / 双臂 / 双腿）映射到当前模型的
 * 实际骨骼名。导入自定义模型时自动弹出：先按命名约定自动识别（
 * {@link SkeletonMapping#autoDetect}），用户逐槽检查、用循环按钮改选骨骼后确认；
 * 确认结果挂载到表单（{@code bbs_ai:skeleton_mapping}），烘焙时据此写入正确骨骼，
 * 并随 .bbsm 导出携带。标准玩家模型保持"(标准)"即可，等价于无映射。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class UIBoneMappingPanel extends UIOverlayPanel
{
    /**
     * 被映射的模型表单
     */
    private final ModelForm form;

    /**
     * 模型全部骨骼名（下拉候选）
     */
    private final List<String> boneNames;

    /**
     * 槽位 → 骨骼循环按钮（索引：0 = "(标准)"，1..n = 模型骨骼）
     */
    private final Map<String, UICirculate> selectors = new LinkedHashMap<>();

    /**
     * 确认回调（导入流程用：确认后再应用表单/通知）
     */
    private final Runnable onConfirm;

    public UIBoneMappingPanel(ModelForm form, Runnable onConfirm)
    {
        super(L10n.lang("bbs_ai.panel.mapping.title"));

        this.form = form;
        this.onConfirm = onConfirm;
        this.boneNames = SkeletonMapping.extractBoneNames(form);

        int margin = 10;
        int y = 6;

        UILabel hint = UI.label(L10n.lang("bbs_ai.panel.mapping.hint"), 12, Colors.GRAY);

        hint.relative(this.content).x(margin).y(y).w(1F, -margin * 2).h(26);
        this.content.add(hint);
        y += 32;

        SkeletonMapping current = SkeletonMapping.get(form);
        UILabel slotHeader = UI.label(L10n.lang("bbs_ai.panel.mapping.slot"), 13, 0xAAAAAA);
        UILabel boneHeader = UI.label(L10n.lang("bbs_ai.panel.mapping.bone"), 13, 0xAAAAAA);

        slotHeader.relative(this.content).x(margin).y(y).w(120);
        boneHeader.relative(this.content).x(1F, -margin - 160).y(y).w(160);
        this.content.add(slotHeader, boneHeader);
        y += 18;

        for (String slot : SkeletonMapper.ALL_BONES)
        {
            UILabel label = UI.label(IKey.constant(slot), 13, Colors.WHITE);

            label.relative(this.content).x(margin).y(y + 3).w(120);
            this.content.add(label);

            UICirculate selector = new UICirculate((c) -> {});

            selector.addLabel(IKey.constant(L10n.lang("bbs_ai.panel.mapping.standard").get()));

            for (String bone : this.boneNames)
            {
                selector.addLabel(IKey.constant(bone));
            }

            selector.relative(this.content).x(1F, -margin - 160).y(y).w(160).h(18);
            this.content.add(selector);

            this.selectors.put(slot, selector);
            this.setSelector(selector, current.getSlot(slot));
            y += 24;
        }

        UIButton auto = new UIButton(L10n.lang("bbs_ai.panel.mapping.auto"), (b) -> this.autoDetect());

        auto.relative(this.content).x(margin).y(1F, -64).w(1F, -margin * 2).h(20);
        this.content.add(auto);

        UIButton confirm = new UIButton(L10n.lang("bbs_ai.panel.mapping.confirm"), (b) -> this.confirmMapping());

        confirm.relative(this.content).x(margin).y(1F, -38).w(1F, -margin * 2 - 96).h(22);
        this.content.add(confirm);

        UIButton clear = new UIButton(L10n.lang("bbs_ai.panel.mapping.clear"), (b) -> this.clearAll());

        clear.relative(this.content).x(1F, -margin - 90).y(1F, -38).w(90).h(22);
        this.content.add(clear);
    }

    /**
     * 把选择器定位到指定模型骨骼（无映射停在"(标准)"）
     */
    private void setSelector(UICirculate selector, String bone)
    {
        int index = bone == null ? 0 : this.boneNames.indexOf(bone) + 1;

        selector.setValue(Math.max(0, index));
    }

    /**
     * 读取选择器当前选中的模型骨骼（0 = 无映射，返回 null）
     */
    private String getSelectorBone(UICirculate selector)
    {
        int index = selector.getValue();

        return index <= 0 ? null : this.boneNames.get(index - 1);
    }

    /**
     * 按命名约定重新自动识别全部槽位
     */
    private void autoDetect()
    {
        SkeletonMapping detected = SkeletonMapping.autoDetect(this.boneNames);

        for (Map.Entry<String, UICirculate> entry : this.selectors.entrySet())
        {
            this.setSelector(entry.getValue(), detected == null ? null : detected.getSlot(entry.getKey()));
        }
    }

    /**
     * 全部槽位恢复标准（无映射）
     */
    private void clearAll()
    {
        for (UICirculate selector : this.selectors.values())
        {
            selector.setValue(0);
        }
    }

    /**
     * 确认：把当前选择写回表单并关闭
     */
    private void confirmMapping()
    {
        SkeletonMapping mapping = SkeletonMapping.get(this.form);

        for (Map.Entry<String, UICirculate> entry : this.selectors.entrySet())
        {
            mapping.setSlot(entry.getKey(), this.getSelectorBone(entry.getValue()));
        }

        mapping.attach(this.form);

        if (this.onConfirm != null)
        {
            this.onConfirm.run();
        }

        this.close();
    }

    /**
     * 汇总当前映射的可读描述（导入通知用）
     */
    public String describe()
    {
        List<String> parts = new ArrayList<>();

        for (Map.Entry<String, UICirculate> entry : this.selectors.entrySet())
        {
            String bone = this.getSelectorBone(entry.getValue());

            if (bone != null && !bone.equals(entry.getKey()))
            {
                parts.add(entry.getKey() + "→" + bone);
            }
        }

        return String.join(", ", parts);
    }
}
