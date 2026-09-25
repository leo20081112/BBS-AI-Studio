package mchorse.bbs_ai.ik;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;


import mchorse.bbs_mod.api.client.editor.FilmEditorTool;
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
    /* 【1.21.11 适配】GPU 管线重写后 WorldRenderContext 不再提供 matrixStack()/camera()，
     * 锚点视口标记暂缺（锚定功能本身经约束面板/角色属性页完整可用）。 */
    @Override
    public void renderTool(WorldRenderContext context)
    {}

    @Override
    public boolean click(UIContext context)
    {
        /* 本工具不消费点击（锚定开关在约束面板/角色属性页），只做可视化 */
        return false;
    }
}
