package mchorse.bbs_ai.ik;

import org.joml.Vector3f;

import mchorse.bbs_ai.core.BBSAISettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;

/**
 * Blender IK 约束设置面板
 *
 * <p>复刻 Blender IK 约束面板布局（项目规范模块 8「设置面板 UI」）：
 * <ul>
 *   <li>目标 / 极向目标坐标输入（带 XYZ 数值框）</li>
 *   <li>极向角度（-180 ~ 180）</li>
 *   <li>链选项：Chain Length、Use Tail、Follow（锚点跟随）</li>
 *   <li>影响：Influence（FK/IK 混合）</li>
 *   <li>拉伸：Stretch、Stretch Limit</li>
 *   <li>目标旋转与权重衰减</li>
 *   <li>「解算并预览」按钮（结果进入预烘焙预览系统）</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BlenderIKSettingsPanel extends UIOverlayPanel
{
    /**
     * 关联的 IK 组件
     */
    private final BlenderIKComponent component;

    /**
     * 解算回调（面板修改参数后触发）
     */
    private final Runnable onSolve;

    public BlenderIKSettingsPanel(BlenderIKComponent component, Runnable onSolve)
    {
        super(IKey.constant("Blender IK 约束（" + component.name + "）"));

        this.component = component;
        this.onSolve = onSolve;

        /* 滚动容器：约束参数较多，小窗口下可滚动查看（修复显示不全） */
        mchorse.bbs_mod.ui.framework.elements.UIScrollView scroll = UI.scrollView();
        scroll.relative(this.content).xy(0, 0).w(1F).h(1F);
        this.content.add(scroll);

        int y = 8;

        /* ---- 目标（Target Empty） ---- */
        y = this.addLabel(L10n.lang("bbs_ai.str.ikPanel.11"), y, scroll);
        y = this.addVectorInputs(this.component.constraint.target, y, scroll);

        /* ---- 极向目标（Pole Target） ---- */
        y = this.addLabel(L10n.lang("bbs_ai.str.ikPanel.12"), y, scroll);
        y = this.addVectorInputs(this.getOrCreatePole(), y, scroll);

        /* ---- 极向角度 ---- */
        y = this.addLabel(L10n.lang("bbs_ai.str.ikPanel.13"), y, scroll);

        UITrackpad poleAngle = new UITrackpad((v) ->
        {
            this.component.constraint.poleAngle = v.floatValue();
            this.solve();
        });

        poleAngle.limit(-180.0F, 180.0F);
        poleAngle.setValue(this.component.constraint.poleAngle);
        poleAngle.relative(scroll).xy(10, y).w(1F, -20).h(20);
        scroll.add(poleAngle);
        y += 26;

        /* ---- 链选项折叠区 ---- */
        y = this.addLabel(L10n.lang("bbs_ai.str.ikPanel.14"), y, scroll);

        UITrackpad chainLength = new UITrackpad((v) ->
        {
            this.component.constraint.chainLength = v.intValue();
            this.solve();
        });

        chainLength.limit(0, 10).integer().setValue(this.component.constraint.chainLength);
        chainLength.relative(scroll).xy(10, y).w(1F, -20).h(20);
        scroll.add(chainLength);
        y += 24;

        UIToggle useTail = new UIToggle(L10n.lang("bbs_ai.str.ikPanel.1"), this.component.constraint.useTail, (b) ->
        {
            this.component.constraint.useTail = b.getValue();
            this.solve();
        });

        useTail.relative(scroll).xy(10, y).w(1F, -20).h(18);
        scroll.add(useTail);
        y += 22;

        UIToggle follow = new UIToggle(L10n.lang("bbs_ai.str.ikPanel.2"), this.component.constraint.useAnchor, (b) ->
        {
            this.component.constraint.useAnchor = b.getValue();
            this.solve();
        });

        follow.relative(scroll).xy(10, y).w(1F, -20).h(18);
        scroll.add(follow);
        y += 26;

        /* ---- FK/IK 混合 ---- */
        y = this.addLabel(L10n.lang("bbs_ai.str.ikPanel.18"), y, scroll);

        UITrackpad influence = new UITrackpad((v) ->
        {
            this.component.constraint.influence = v.floatValue();
            this.solve();
        });

        influence.limit(0.0F, 1.0F).setValue(this.component.constraint.influence);
        influence.relative(scroll).xy(10, y).w(1F, -20).h(20);
        scroll.add(influence);
        y += 26;

        /* ---- 拉伸折叠区 ---- */
        y = this.addLabel(L10n.lang("bbs_ai.str.ikPanel.15"), y, scroll);

        UIToggle stretch = new UIToggle(L10n.lang("bbs_ai.str.ikPanel.3"), this.component.constraint.useStretch, (b) ->
        {
            this.component.constraint.useStretch = b.getValue();
            this.solve();
        });

        stretch.relative(scroll).xy(10, y).w(1F, -20).h(18);
        scroll.add(stretch);
        y += 22;

        UITrackpad stretchLimit = new UITrackpad((v) ->
        {
            this.component.constraint.stretchLimit = v.floatValue();
            this.solve();
        });

        stretchLimit.limit(1.0F, 2.0F).setValue(this.component.constraint.stretchLimit);
        stretchLimit.relative(scroll).xy(10, y).w(1F, -20).h(20);
        scroll.add(stretchLimit);
        y += 26;

        /* ---- 目标旋转与权重衰减 ---- */
        UIToggle targetRotation = new UIToggle(L10n.lang("bbs_ai.str.ikPanel.4"), this.component.constraint.useTargetRotation, (b) ->
        {
            this.component.constraint.useTargetRotation = b.getValue();
            this.solve();
        });

        targetRotation.relative(scroll).xy(10, y).w(1F, -20).h(18);
        scroll.add(targetRotation);
        y += 22;

        UIToggle falloff = new UIToggle(L10n.lang("bbs_ai.str.ikPanel.5"), this.component.constraint.useWeightFalloff, (b) ->
        {
            this.component.constraint.useWeightFalloff = b.getValue();
            this.solve();
        });

        falloff.relative(scroll).xy(10, y).w(1F, -20).h(18);
        scroll.add(falloff);
        y += 26;

        /* ---- 锚点跟随（手/脚旋转时的锚点联动） ---- */
        y = this.addLabel(L10n.lang("bbs_ai.str.ikPanel.19"), y, scroll);

        mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate anchorMode = new mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate((c) ->
        {
            this.component.constraint.anchorMode = c.getValue();
            this.solve();
        });

        anchorMode.addLabel(L10n.lang("bbs_ai.str.ikPanel.6"));
        anchorMode.addLabel(L10n.lang("bbs_ai.str.ikPanel.7"));
        anchorMode.addLabel(L10n.lang("bbs_ai.str.ikPanel.8"));
        anchorMode.setValue(this.component.constraint.anchorMode);
        anchorMode.relative(scroll).xy(10, y).w(1F, -20).h(20);
        scroll.add(anchorMode);
        y += 24;

        UITrackpad anchorStrength = new UITrackpad((v) ->
        {
            this.component.constraint.anchorStrength = v.floatValue();
            this.solve();
        });

        anchorStrength.limit(0.0F, 1.0F).setValue(this.component.constraint.anchorStrength);
        anchorStrength.relative(scroll).xy(10, y).w(1F, -20).h(20);
        scroll.add(anchorStrength);
        y += 24;

        UITrackpad anchorRelease = new UITrackpad((v) ->
        {
            this.component.constraint.anchorReleaseAngle = v.floatValue();
            this.solve();
        });

        anchorRelease.limit(1.0F, 90.0F).setValue(this.component.constraint.anchorReleaseAngle);
        anchorRelease.relative(scroll).xy(10, y).w(1F, -20).h(20);
        scroll.add(anchorRelease);
        y += 24;

        UILabel anchoredState = new UILabel(L10n.lang("bbs_ai.str.ikPanel.9"));

        anchoredState.color(0x666666);
        anchoredState.relative(scroll).xy(10, y).w(1F, -20);
        scroll.add(anchoredState);
        y += 18;

        /* ---- 解算按钮 ---- */
        UIButton solve = new UIButton(L10n.lang("bbs_ai.str.toolsPanel.8"), (b) -> this.solve());

        solve.relative(scroll).xy(10, y).w(1F, -20).h(20);
        scroll.add(solve);
    }

    /**
     * 添加分组标签
     *
     * @return 下一行 y
     */
    private int addLabel(mchorse.bbs_mod.l10n.keys.IKey text, int y, mchorse.bbs_mod.ui.framework.elements.UIScrollView scroll)
    {
        UILabel label = new UILabel(text);

        label.color(0xAAAAAA);
        label.relative(scroll).xy(10, y).w(1F, -20);
        scroll.add(label);

        return y + 16;
    }

    /**
     * 添加 XYZ 数值输入
     *
     * @return 下一行 y
     */
    private int addVectorInputs(Vector3f vector, int y, mchorse.bbs_mod.ui.framework.elements.UIScrollView scroll)
    {
        String[] axes = {"X", "Y", "Z"};

        for (int i = 0; i < 3; i++)
        {
            final int axis = i;

            UITrackpad trackpad = new UITrackpad((v) ->
            {
                vector.setComponent(axis, v.floatValue());
                this.solve();
            });

            trackpad.limit(-100.0F, 100.0F);
            trackpad.setValue(vector.get(i));

            UILabel axisLabel = new UILabel(IKey.constant(axes[i]));

            axisLabel.relative(scroll).xy(10, y + 4).w(14);
            trackpad.relative(scroll).x(26).y(y).w(1F, -36).h(18);

            scroll.add(axisLabel, trackpad);

            y += 20;
        }

        return y + 6;
    }

    /**
     * 获取或创建极向目标
     */
    private Vector3f getOrCreatePole()
    {
        if (this.component.constraint.poleTarget == null)
        {
            this.component.constraint.poleTarget = new Vector3f(0.0F, 1.0F, 0.0F);
        }

        return this.component.constraint.poleTarget;
    }

    /**
     * 触发解算（进入预览）
     */
    private void solve()
    {
        /* 同步默认链长度（约束为 0 = 全链时使用设置值） */
        if (this.component.constraint.chainLength <= 0 && BBSAISettings.ikDefaultChainLength != null)
        {
            /* 0 表示整条链，保持 0 */
        }

        if (this.onSolve != null)
        {
            this.onSolve.run();
        }
    }
}
