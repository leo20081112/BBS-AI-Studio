package mchorse.bbs_ai.ui.context;

import java.util.ArrayList;
import java.util.List;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.model_editor.UIModelEditorPanel;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;

/**
 * 独立上下文系统（AI Workspace Context）
 *
 * <p>追踪「用户此刻在哪个界面、正在编辑什么」，为任意界面的 AI 语义调整提供上下文：
 * <ul>
 *   <li>当前仪表盘面板（影片/模型编辑/变形/粒子/AI 面板…）</li>
 *   <li>当前打开影片（相机剪辑数/角色数）与选中状态</li>
 *   <li>模型编辑页正在编辑的 ModelForm</li>
 *   <li>变形面板当前佩戴的形态</li>
 * </ul>
 * AI 编辑器对话、AI 助手菜单、分镜/动作生成都从这里读取「现在该针对什么工作」，
 * 而不是各自维护碎片状态（消除历史遗留的割裂）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class AIWorkspaceContext
{
    /**
     * 单例
     */
    private static final AIWorkspaceContext INSTANCE = new AIWorkspaceContext();

    /**
     * 当前面板 id（film / model_editor / morphing / ai_editor / ai_tools / other）
     */
    public String panelId = "other";

    /**
     * 当前打开的影片（来自影片面板，null = 未打开）
     */
    public Film film;

    /**
     * 模型编辑页正在编辑的模型表单（null = 无）
     */
    public ModelForm modelForm;

    /**
     * 变形面板当前形态是否已设置
     */
    public boolean morphActive;

    private AIWorkspaceContext()
    {}

    public static AIWorkspaceContext get()
    {
        return INSTANCE;
    }

    /**
     * 从仪表盘刷新上下文（AI 编辑器/AI 助手菜单打开时调用）
     */
    public void refresh(UIDashboard dashboard)
    {
        if (dashboard == null)
        {
            return;
        }

        Object panel = dashboard.getPanels().panel;

        if (panel == null)
        {
            this.panelId = "other";
            this.film = null;
            this.modelForm = null;

            return;
        }

        String name = panel.getClass().getSimpleName();

        if (panel instanceof UIFilmPanel)
        {
            this.panelId = "film";
            this.film = ((UIFilmPanel) panel).getData();
        }
        else if (panel instanceof UIModelEditorPanel)
        {
            this.panelId = "model_editor";
            this.modelForm = ((UIModelEditorPanel) panel).getForm();
        }
        else if (panel instanceof UIMorphingPanel)
        {
            this.panelId = "morphing";
            this.morphActive = true;
        }
        else if (name.contains("AI"))
        {
            this.panelId = "ai";
        }
        else
        {
            this.panelId = "other";
        }
    }

    /**
     * 语义上下文描述（注入 AI 对话系统提示词 / 助手菜单说明）
     */
    public String describe()
    {
        List<String> parts = new ArrayList<>();

        if (this.film != null)
        {
            parts.add("影片编辑中（" + this.film.replays.getList().size() + " 角色、"
                + this.film.camera.getClips(Clip.class).size() + " 相机剪辑）");
        }
        else
        {
            parts.add("当前未打开影片");
        }

        if (this.modelForm != null)
        {
            parts.add("正在编辑人物模型（" + this.modelForm.model.get() + "）");
        }

        if (this.morphActive)
        {
            parts.add("变形面板活动中（可推荐演员形态）");
        }

        return String.join("；", parts);
    }

    /**
     * 面板 id 常量便捷判断
     */
    public boolean isFilm()
    {
        return "film".equals(this.panelId);
    }

    /**
     * 便捷获取仪表盘（供面板外调用）
     */
    public static UIDashboard dashboard()
    {
        return BBSModClient.getDashboard();
    }

    /**
     * 便捷刷新（BBSMod.events/BBSModClient 均可触达）
     */
    public static AIWorkspaceContext refreshNow()
    {
        INSTANCE.refresh(BBSModClient.getDashboard());

        return INSTANCE;
    }

}
