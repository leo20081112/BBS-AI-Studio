package mchorse.bbs_ai.ik;

import org.joml.Vector3f;

import mchorse.bbs_ai.core.BBSAISettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;

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

        int y = 8;

        /* ---- 目标（Target Empty） ---- */
        y = this.addLabel("目标 (Target)", y);
        y = this.addVectorInputs(this.component.constraint.target, y);

        /* ---- 极向目标（Pole Target） ---- */
        y = this.addLabel("极向目标 (Pole Target)", y);
        y = this.addVectorInputs(this.getOrCreatePole(), y);

        /* ---- 极向角度 ---- */
        y = this.addLabel("极向角度 (Pole Angle)", y);

        UITrackpad poleAngle = new UITrackpad((v) ->
        {
            this.component.constraint.poleAngle = v.floatValue();
            this.solve();
        });

        poleAngle.limit(-180.0F, 180.0F);
        poleAngle.setValue(this.component.constraint.poleAngle);
        poleAngle.relative(this.content).xy(10, y).w(1F, -20).h(20);
        this.content.add(poleAngle);
        y += 26;

        /* ---- 链选项折叠区 ---- */
        y = this.addLabel("链选项", y);

        UITrackpad chainLength = new UITrackpad((v) ->
        {
            this.component.constraint.chainLength = v.intValue();
            this.solve();
        });

        chainLength.limit(0, 10).integer().setValue(this.component.constraint.chainLength);
        chainLength.relative(this.content).xy(10, y).w(1F, -20).h(20);
        this.content.add(chainLength);
        y += 24;

        UIToggle useTail = new UIToggle(IKey.constant("使用末端 (Use Tail)"), this.component.constraint.useTail, (b) ->
        {
            this.component.constraint.useTail = b.getValue();
            this.solve();
        });

        useTail.relative(this.content).xy(10, y).w(1F, -20).h(18);
        this.content.add(useTail);
        y += 22;

        UIToggle follow = new UIToggle(IKey.constant("锚点跟随 (Follow)"), this.component.constraint.useAnchor, (b) ->
        {
            this.component.constraint.useAnchor = b.getValue();
            this.solve();
        });

        follow.relative(this.content).xy(10, y).w(1F, -20).h(18);
        this.content.add(follow);
        y += 26;

        /* ---- FK/IK 混合 ---- */
        y = this.addLabel("FK/IK 混合 (Influence)", y);

        UITrackpad influence = new UITrackpad((v) ->
        {
            this.component.constraint.influence = v.floatValue();
            this.solve();
        });

        influence.limit(0.0F, 1.0F).setValue(this.component.constraint.influence);
        influence.relative(this.content).xy(10, y).w(1F, -20).h(20);
        this.content.add(influence);
        y += 26;

        /* ---- 拉伸折叠区 ---- */
        y = this.addLabel("拉伸 (Stretch)", y);

        UIToggle stretch = new UIToggle(IKey.constant("使用拉伸"), this.component.constraint.useStretch, (b) ->
        {
            this.component.constraint.useStretch = b.getValue();
            this.solve();
        });

        stretch.relative(this.content).xy(10, y).w(1F, -20).h(18);
        this.content.add(stretch);
        y += 22;

        UITrackpad stretchLimit = new UITrackpad((v) ->
        {
            this.component.constraint.stretchLimit = v.floatValue();
            this.solve();
        });

        stretchLimit.limit(1.0F, 2.0F).setValue(this.component.constraint.stretchLimit);
        stretchLimit.relative(this.content).xy(10, y).w(1F, -20).h(20);
        this.content.add(stretchLimit);
        y += 26;

        /* ---- 目标旋转与权重衰减 ---- */
        UIToggle targetRotation = new UIToggle(IKey.constant("使用目标旋转"), this.component.constraint.useTargetRotation, (b) ->
        {
            this.component.constraint.useTargetRotation = b.getValue();
            this.solve();
        });

        targetRotation.relative(this.content).xy(10, y).w(1F, -20).h(18);
        this.content.add(targetRotation);
        y += 22;

        UIToggle falloff = new UIToggle(IKey.constant("权重衰减 (Weight Falloff)"), this.component.constraint.useWeightFalloff, (b) ->
        {
            this.component.constraint.useWeightFalloff = b.getValue();
            this.solve();
        });

        falloff.relative(this.content).xy(10, y).w(1F, -20).h(18);
        this.content.add(falloff);
        y += 26;

        /* ---- 锚点跟随（手/脚旋转时的锚点联动） ---- */
        y = this.addLabel("锚点跟随（手/脚）", y);

        mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate anchorMode = new mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate((c) ->
        {
            this.component.constraint.anchorMode = c.getValue();
            this.solve();
        });

        anchorMode.addLabel(IKey.constant("无"));
        anchorMode.addLabel(IKey.constant("脚部贴地"));
        anchorMode.addLabel(IKey.constant("手部抓附"));
        anchorMode.setValue(this.component.constraint.anchorMode);
        anchorMode.relative(this.content).xy(10, y).w(1F, -20).h(20);
        this.content.add(anchorMode);
        y += 24;

        UITrackpad anchorStrength = new UITrackpad((v) ->
        {
            this.component.constraint.anchorStrength = v.floatValue();
            this.solve();
        });

        anchorStrength.limit(0.0F, 1.0F).setValue(this.component.constraint.anchorStrength);
        anchorStrength.relative(this.content).xy(10, y).w(1F, -20).h(20);
        this.content.add(anchorStrength);
        y += 24;

        UITrackpad anchorRelease = new UITrackpad((v) ->
        {
            this.component.constraint.anchorReleaseAngle = v.floatValue();
            this.solve();
        });

        anchorRelease.limit(1.0F, 90.0F).setValue(this.component.constraint.anchorReleaseAngle);
        anchorRelease.relative(this.content).xy(10, y).w(1F, -20).h(20);
        this.content.add(anchorRelease);
        y += 24;

        UILabel anchoredState = new UILabel(IKey.constant("释放阈值：末端旋转偏移超过该角度后解除锚定（脚抬步/手松开）"));

        anchoredState.color(0x666666);
        anchoredState.relative(this.content).xy(10, y).w(1F, -20);
        this.content.add(anchoredState);
        y += 18;

        /* ---- 解算按钮 ---- */
        UIButton solve = new UIButton(IKey.constant("解算并预览"), (b) -> this.solve());

        solve.relative(this.content).xy(10, y).w(1F, -20).h(20);
        this.content.add(solve);
    }

    /**
     * 添加分组标签
     *
     * @return 下一行 y
     */
    private int addLabel(String text, int y)
    {
        UILabel label = new UILabel(IKey.constant(text));

        label.color(0xAAAAAA);
        label.relative(this.content).xy(10, y).w(1F, -20);
        this.content.add(label);

        return y + 16;
    }

    /**
     * 添加 XYZ 数值输入
     *
     * @return 下一行 y
     */
    private int addVectorInputs(Vector3f vector, int y)
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

            axisLabel.relative(this.content).xy(10, y + 4).w(14);
            trackpad.relative(this.content).x(26).y(y).w(1F, -36).h(18);

            this.content.add(axisLabel, trackpad);

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
