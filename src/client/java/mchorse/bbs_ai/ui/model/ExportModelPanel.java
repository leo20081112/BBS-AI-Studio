package mchorse.bbs_ai.ui.model;

import net.minecraft.client.MinecraftClient;

import mchorse.bbs_ai.model.ModelExporter;
import mchorse.bbs_ai.model.ModelImporter;
import mchorse.bbs_ai.BBSAIStudio;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;

/**
 * 导出人物模型对话框（Ctrl+Shift+E）
 *
 * <p>把当前选中角色的 ModelForm 导出为 .bbsm 单文件
 * （GZIP + JSON，内嵌几何 / 骨骼 / 纹理 / 姿态 / IK / 物理 / 动作），
 * 输出到 {@code config/bbs/models/<名称>.bbsm}。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ExportModelPanel extends UIOverlayPanel
{
    /**
     * 导出目标表单（无选中角色时 null）
     */
    private final ModelForm targetForm;

    /**
     * 模型名称
     */
    private final UITextbox nameField;

    /**
     * 作者
     */
    private final UITextbox authorField;

    /**
     * 描述
     */
    private final UITextarea descriptionField;

    /**
     * 状态提示
     */
    private final UILabel statusLabel;

    public ExportModelPanel()
    {
        super(L10n.lang("bbs_ai.panel.model.export.title"));

        this.targetForm = ModelFormUI.resolveCurrentModelForm();

        int y = 6;
        int margin = 10;

        UILabel nameLabel = UI.label(L10n.lang("bbs_ai.panel.model.export.name"), 14, 0xAAAAAA);

        nameLabel.relative(this.content).xy(margin, y).w(1F, -margin * 2);
        this.content.add(nameLabel);
        y += 16;

        String defaultName = "";

        if (this.targetForm != null)
        {
            String modelId = this.targetForm.model.get();

            defaultName = modelId == null || modelId.isEmpty() ? this.targetForm.getDisplayName() : modelId;
        }

        this.nameField = new UITextbox(128, (t) -> {});
        this.nameField.setText(defaultName);
        this.nameField.relative(this.content).x(margin).y(y).w(1F, -margin * 2).h(20);
        this.content.add(this.nameField);
        y += 26;

        UILabel authorLabel = UI.label(L10n.lang("bbs_ai.panel.model.export.author"), 14, 0xAAAAAA);

        authorLabel.relative(this.content).xy(margin, y).w(1F, -margin * 2);
        this.content.add(authorLabel);
        y += 16;

        String username = "";

        try
        {
            username = MinecraftClient.getInstance().getSession().getUsername();
        }
        catch (Exception ignored)
        {}

        this.authorField = new UITextbox(128, (t) -> {});
        this.authorField.setText(username);
        this.authorField.relative(this.content).x(margin).y(y).w(1F, -margin * 2).h(20);
        this.content.add(this.authorField);
        y += 26;

        UILabel descLabel = UI.label(L10n.lang("bbs_ai.panel.model.export.desc"), 14, 0xAAAAAA);

        descLabel.relative(this.content).xy(margin, y).w(1F, -margin * 2);
        this.content.add(descLabel);
        y += 16;

        this.descriptionField = new UITextarea((t) -> {});
        this.descriptionField.background().wrap();
        this.descriptionField.relative(this.content).x(margin).y(y).w(1F, -margin * 2).h(1F, -100);
        this.content.add(this.descriptionField);

        /* 底部：状态 + 导出按钮 + 路径提示 */
        this.statusLabel = UI.label(IKey.constant(" "), 14, 0xAAAAAA);
        this.statusLabel.relative(this.content).x(margin).y(1F, -72).w(1F, -margin * 2);
        this.content.add(this.statusLabel);

        UIButton export = new UIButton(L10n.lang("bbs_ai.panel.model.export.button"), (b) -> this.performExport());

        export.relative(this.content).x(margin).y(1F, -50).w(1F, -margin * 2).h(22);
        this.content.add(export);

        UILabel path = UI.label(L10n.lang("bbs_ai.panel.model.export.path"), 14, 0x666666);

        path.relative(this.content).x(margin).y(1F, -24).w(1F, -margin * 2);
        this.content.add(path);

        if (this.targetForm == null)
        {
            this.statusLabel.label = L10n.lang("bbs_ai.panel.model.export.no_form");
        }
    }

    /**
     * 执行导出
     */
    private void performExport()
    {
        if (this.targetForm == null)
        {
            return;
        }

        String name = this.nameField.getText().trim();

        if (name.isEmpty())
        {
            this.statusLabel.label = L10n.lang("bbs_ai.panel.model.export.no_name");

            return;
        }

        try
        {
            String fileName = ModelImporter.sanitizeId(name) + ".bbsm";
            ModelExporter.ExportResult result = ModelExporter.export(
                this.targetForm,
                name,
                this.authorField.getText().trim(),
                this.descriptionField.getText(),
                BBSAIStudio.getModelExportPath(fileName)
            );

            this.statusLabel.label = IKey.constant("√ " + result.summary());

            this.getContext().notifyInfo(IKey.constant("√ " + L10n.lang("bbs_ai.panel.model.export.done") + " " + fileName));
        }
        catch (Exception e)
        {
            System.err.println("[BBS AI] 导出模型失败：" + e);

            this.statusLabel.label = IKey.constant("X " + e.getMessage());
            this.getContext().notifyInfo(IKey.constant("X " + L10n.lang("bbs_ai.panel.model.export.fail") + " " + e.getMessage()));
        }
    }
}
