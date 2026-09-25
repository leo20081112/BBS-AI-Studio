package mchorse.bbs_ai.integration;

import mchorse.bbs_ai.ik.AIAnchorConfig;
import mchorse.bbs_ai.ik.AIAnchorTool;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.client.events.FormPoseEvents;
import mchorse.bbs_mod.api.client.events.RegisterFilmToolsEvent;
import mchorse.bbs_mod.api.client.events.RegisterReplayActionsEvent;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;

/**
 * 2.7 新体系事件监听（AI 锚定接入）
 *
 * <p>三层锚定的新体系部分：
 * <ul>
 *   <li>{@code RegisterFilmToolsEvent} —— AI 锚定视口工具（锚点标记可视化）</li>
 *   <li>{@code RegisterReplayActionsEvent} —— 角色属性页注册锚定控件
 *       （模式：无/脚部贴地/手部抓附；强度；释放阈值），与约束面板共享同一约束</li>
 * </ul>
 * 控件绑定 {@link AIAnchorConfig#COMPONENT}（与约束面板同源，状态一致）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class AIEventListeners
{
    static
    {
        /* M3③ 渲染链锚定补偿：动画与 IK 之后（RENDER pass），
         * 脚部贴地模式下把模型 base 沿 Y 向锚定地面回位（强度加权）。
         * 近似语义：worldOrigin（链根）= 锚定参考体，target = 贴地锚点，
         * 二者 Y 差即身体当前偏离锚定平面的量。 */
        FormPoseEvents.MODEL_POSE.register((form, entity, model, transition, base, pass) ->
        {
            var constraint = AIAnchorConfig.COMPONENT.constraint;

            if (pass != FormPoseEvents.Pass.RENDER
                || constraint.anchorMode != 1
                || !constraint.anchored
                || constraint.worldOrigin == null)
            {
                return;
            }

            float offsetY = (constraint.worldOrigin.y - constraint.target.y) * constraint.anchorStrength;

            base.translate(0.0F, offsetY, 0.0F);
        });
    }

    @Subscribe
    public void onRegisterFilmTools(RegisterFilmToolsEvent event)
    {
        event.register((controller) -> new AIAnchorTool());
    }

    @Subscribe
    public void onRegisterReplayActions(RegisterReplayActionsEvent event)
    {
        event.register((panel, replaySupplier) ->
        {
            UIElement column = new UIElement();

            column.column(3).stretch().vertical().height(20).padding(2);

            UILabel title = new UILabel(IKey.constant("AI 锚点跟随（手/脚）"));

            title.color(0xAAAAAA);
            column.add(title);

            UICirculate mode = new UICirculate((c) ->
            {
                AIAnchorConfig.COMPONENT.constraint.anchorMode = c.getValue();
            });

            mode.addLabel(IKey.constant("无"));
            mode.addLabel(IKey.constant("脚部贴地"));
            mode.addLabel(IKey.constant("手部抓附"));
            mode.setValue(AIAnchorConfig.COMPONENT.constraint.anchorMode);
            column.add(mode);

            UITrackpad strength = new UITrackpad((v) ->
            {
                AIAnchorConfig.COMPONENT.constraint.anchorStrength = v.floatValue();
            });

            strength.limit(0.0F, 1.0F).setValue(AIAnchorConfig.COMPONENT.constraint.anchorStrength);
            column.add(UI.labelRow(IKey.constant("强度"), strength));

            UITrackpad release = new UITrackpad((v) ->
            {
                AIAnchorConfig.COMPONENT.constraint.anchorReleaseAngle = v.floatValue();
            });

            release.limit(1.0F, 90.0F).setValue(AIAnchorConfig.COMPONENT.constraint.anchorReleaseAngle);
            column.add(UI.labelRow(IKey.constant("释放角度"), release));

            return column;
        });
    }
}
