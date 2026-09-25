package mchorse.bbs_ai.ui.model;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;

/**
 * 模型导出/导入的编辑器上下文工具
 *
 * <p>定位当前选中的 ModelForm（影片面板 → 回放编辑器 → 当前 Replay 的表单）
 * 并把导入的新表单应用回当前 Replay【原版兼容：直接走
 * {@code Replay.form.set}，编辑器即时渲染即为预览】。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ModelFormUI
{
    /**
     * 获取当前选中的 ModelForm（无选中或不是模型表单时返回 null）
     */
    public static ModelForm resolveCurrentModelForm()
    {
        try
        {
            UIDashboard dashboard = BBSModClient.getDashboardIfCreated();

            if (dashboard == null)
            {
                return null;
            }

            UIFilmPanel filmPanel = dashboard.getPanel(UIFilmPanel.class);

            if (filmPanel == null || filmPanel.replayEditor == null)
            {
                return null;
            }

            Replay replay = filmPanel.replayEditor.getReplay();

            if (replay != null && replay.form.get() instanceof ModelForm modelForm)
            {
                return modelForm;
            }
        }
        catch (Exception e)
        {
            System.err.println("[BBS AI] 定位当前 ModelForm 失败：" + e.getMessage());
        }

        return null;
    }

    /**
     * 把导入的 ModelForm 应用到当前选中角色
     *
     * @return 是否应用成功（无选中角色时 false）
     */
    public static boolean applyImportedForm(ModelForm form)
    {
        try
        {
            UIDashboard dashboard = BBSModClient.getDashboardIfCreated();

            if (dashboard == null)
            {
                return false;
            }

            UIFilmPanel filmPanel = dashboard.getPanel(UIFilmPanel.class);

            if (filmPanel == null || filmPanel.replayEditor == null)
            {
                return false;
            }

            Replay replay = filmPanel.replayEditor.getReplay();

            if (replay == null)
            {
                return false;
            }

            replay.form.set(form);

            return true;
        }
        catch (Exception e)
        {
            System.err.println("[BBS AI] 应用导入表单失败：" + e.getMessage());

            return false;
        }
    }
}
