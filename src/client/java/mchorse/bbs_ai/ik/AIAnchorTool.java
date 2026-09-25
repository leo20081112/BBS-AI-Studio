package mchorse.bbs_ai.ik;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import org.joml.Vector3f;

import mchorse.bbs_mod.api.client.editor.FilmEditorTool;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.ui.framework.UIContext;

/**
 * AI 锚定视口工具（bbs-fs 2.7 新体系 M1）
 *
 * <p>经 {@code RegisterFilmToolsEvent} 注册进影片编辑器：当共享 IK 组件处于
 * 「脚部贴地/手部抓附」锚定状态时，在视口里渲染锚定点标记（世界空间小盒），
 * 让用户在拍摄现场直接看到锚定位置与状态。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class AIAnchorTool extends FilmEditorTool
{
    @Override
    public void renderTool(WorldRenderContext context)
    {
        if (!AIAnchorConfig.COMPONENT.constraint.anchored)
        {
            return;
        }

        Vector3f anchor = AIAnchorConfig.COMPONENT.constraint.anchorPoint;

        if (anchor == null)
        {
            return;
        }

        var stack = context.matrixStack();
        var camera = context.camera().getPos();

        stack.push();
        stack.translate(anchor.x - camera.x, anchor.y - camera.y, anchor.z - camera.z);

        /* 锚点标记：0.06 格亮绿小盒（世界空间） */
        Draw.renderBox(stack, -0.03, -0.03, -0.03, 0.06, 0.06, 0.06, 0.5F, 1.0F, 0.6F, 0.9F);

        stack.pop();
    }

    @Override
    public boolean click(UIContext context)
    {
        /* 本工具不消费点击（锚定开关在约束面板/角色属性页），只做可视化 */
        return false;
    }
}
