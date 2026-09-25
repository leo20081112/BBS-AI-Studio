package mchorse.bbs_ai.ui.model;

import java.util.ArrayList;
import java.util.List;

import mchorse.bbs_ai.model.ModelBrowser;
import mchorse.bbs_ai.model.ModelEntry;
import mchorse.bbs_ai.model.ModelImporter;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;

/**
 * 模型浏览器对话框（Ctrl+Shift+O 导入 / Ctrl+Alt+M 浏览）
 *
 * <p>扫描导出目录、外部导入目录与游戏内模型目录，列出全部可导入模型
 * （.bbsm / .bbs.json / .bobj），支持关键字过滤；
 * 导入即解包 .bbsm 单文件到 {@code assets/models/}，并应用到当前选中角色
 * 【原版兼容：{@code Replay.form.set}，编辑器即时渲染即为预览】。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ModelBrowserPanel extends UIOverlayPanel
{
    /**
     * 全部扫描结果
     */
    private List<ModelEntry> allEntries = new ArrayList<>();

    /**
     * 过滤后与列表对应的条目
     */
    private List<ModelEntry> filteredEntries = new ArrayList<>();

    /**
     * 搜索框
     */
    private final UITextbox searchField;

    /**
     * 模型列表
     */
    private final UIStringList modelList;

    /**
     * 详情标签
     */
    private final UILabel infoLabel;

    public ModelBrowserPanel()
    {
        super(L10n.lang("bbs_ai.panel.model.browser.title"));

        int margin = 10;
        int y = 6;

        this.searchField = new UITextbox(256, (t) -> this.applyFilter());
        this.searchField.placeholder(L10n.lang("bbs_ai.panel.model.browser.search"));
        this.searchField.relative(this.content).x(margin).y(y).w(1F, -margin * 2 - 90).h(20);
        this.content.add(this.searchField);

        UIButton refresh = new UIButton(L10n.lang("bbs_ai.panel.model.browser.refresh"), (b) -> this.rescan());

        refresh.relative(this.content).x(1F, -margin - 84).y(y).w(84).h(20);
        this.content.add(refresh);
        y += 26;

        this.modelList = new UIStringList((l) -> this.updateInfo());
        this.modelList.relative(this.content).x(margin).y(y).w(1F, -margin * 2).h(1F, -110);
        this.modelList.background();
        this.content.add(this.modelList);

        this.infoLabel = UI.label(L10n.lang("bbs_ai.panel.model.browser.empty"), 14, 0xAAAAAA);
        this.infoLabel.relative(this.content).x(margin).y(1F, -78).w(1F, -margin * 2).h(44);
        this.content.add(this.infoLabel);

        UIButton importButton = new UIButton(L10n.lang("bbs_ai.panel.model.browser.import"), (b) -> this.importSelected());

        importButton.relative(this.content).x(margin).y(1F, -32).w(1F, -margin * 2).h(22);
        this.content.add(importButton);

        this.rescan();
    }

    /**
     * 重新扫描目录并刷新列表
     */
    private void rescan()
    {
        this.allEntries = ModelBrowser.scan();
        this.applyFilter();
    }

    /**
     * 应用搜索过滤并重建列表
     */
    private void applyFilter()
    {
        this.filteredEntries = ModelBrowser.filter(this.allEntries, this.searchField.getText());

        this.modelList.clear();

        for (ModelEntry entry : this.filteredEntries)
        {
            this.modelList.add(entry.toListLabel());
        }

        this.modelList.setIndex(-1);

        if (this.filteredEntries.isEmpty())
        {
            this.infoLabel.label = L10n.lang("bbs_ai.panel.model.browser.empty");
        }
    }

    /**
     * 更新选中条目详情
     */
    private void updateInfo()
    {
        ModelEntry entry = this.getSelectedEntry();

        if (entry == null)
        {
            return;
        }

        this.infoLabel.label = IKey.constant(entry.toDetailLabel());
    }

    /**
     * 获取选中条目
     */
    private ModelEntry getSelectedEntry()
    {
        int index = this.modelList.getIndex();

        return index >= 0 && index < this.filteredEntries.size() ? this.filteredEntries.get(index) : null;
    }

    /**
     * 导入选中模型：解包到 assets → 重载模型管理器 → 应用到当前角色 →
     * 自定义模型弹出骨骼映射面板让用户检查/修改/确认
     */
    private void importSelected()
    {
        ModelEntry entry = this.getSelectedEntry();

        if (entry == null)
        {
            this.infoLabel.label = L10n.lang("bbs_ai.panel.model.browser.no_select");

            return;
        }

        try
        {
            mchorse.bbs_mod.forms.forms.ModelForm form = ModelImporter.importFromFile(entry.file);

            /* 重载模型管理器，让新解包的模型目录立即可用 */
            BBSModClient.getModels().reload();

            boolean applied = ModelFormUI.applyImportedForm(form);

            this.close();

            this.getContext().notifyInfo(IKey.constant(
                "√ " + L10n.lang("bbs_ai.panel.model.browser.done") + ": " + entry.name
                + (applied ? "" : "（" + L10n.lang("bbs_ai.panel.model.browser.not_applied") + "）")
            ));

            /* 骨骼映射：自定义模型自动弹出确认面板（标准玩家模型不打扰） */
            this.openMappingPanelIfNeeded(form);
        }
        catch (Exception e)
        {
            System.err.println("[BBS AI] 导入模型失败：" + e);

            this.infoLabel.label = IKey.constant("X " + e.getMessage());
        }
    }

    /**
     * 需要时弹出骨骼映射面板：模型带骨骼且不是恰好标准六骨时，
     * 自动识别预填，由用户检查、修改后确认；已携带映射的 .bbsm 同样弹出复查
     */
    private void openMappingPanelIfNeeded(mchorse.bbs_mod.forms.forms.ModelForm form)
    {
        try
        {
            java.util.List<String> boneNames = mchorse.bbs_ai.motion.SkeletonMapping.extractBoneNames(form);

            if (boneNames.isEmpty())
            {
                return;
            }

            boolean standard = mchorse.bbs_ai.motion.SkeletonMapper.boneNames().equals(boneNames);

            if (standard && mchorse.bbs_ai.motion.SkeletonMapping.get(form).isEmpty())
            {
                return;
            }

            mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay.addOverlay(
                this.getContext(),
                new mchorse.bbs_ai.ui.model.UIBoneMappingPanel(form, null),
                340,
                320
            );
        }
        catch (Exception e)
        {
            System.err.println("[BBS AI] 打开骨骼映射面板失败：" + e.getMessage());
        }
    }
}
