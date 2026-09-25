package mchorse.bbs_mod.ui.morphing;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.IMorphProvider;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.morphing.camera.ImmersiveMorphingCameraController;
import mchorse.bbs_mod.ui.onboarding.TourAnchors;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

public class UIMorphingPanel extends UIDashboardPanel
{
    public UIFormPalette palette;
    public UIIcon demorph;
    public UIIcon aiAssist;
    public UIIcon fromMob;

    private ImmersiveMorphingCameraController controller;

    public UIMorphingPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.palette = new UIFormPalette(this::setForm);
        this.palette.updatable().cantExit();
        this.palette.immersive();
        this.palette.full(this);
        this.palette.editor.renderer.full(dashboard.getRoot());
        this.palette.noBackground();
        this.palette.canModify();

        this.demorph = new UIIcon(Icons.POSE, (b) ->
        {
            this.palette.setSelected(null);
            this.setForm(null);
        });
        this.demorph.tooltip(UIKeys.MORPHING_DEMORPH, Direction.TOP);
        this.fromMob = new UIIcon(Icons.MORPH, (b) ->
        {
            Form form = Morph.getMobForm(MinecraftClient.getInstance().player);

            if (form != null)
            {
                this.palette.setSelected(form);
                this.setForm(form);
            }
        });
        this.fromMob.tooltip(UIKeys.MORPHING_FROM_MOB, Direction.TOP);

        /* 【BBS AI Studio】变形 AI 助手：按当前 mod 环境推荐可用演员形态 */
        this.aiAssist = new UIIcon(Icons.MAZE, (b) ->
        {
            mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay.addOverlay(
                this.getContext(),
                new mchorse.bbs_ai.ui.ai.UIAIAssistMenu(
                    "变形 AI 助手",
                    "根据当前 mod 环境推荐可用的演员形态（实体形态可直接入镜）",
                    java.util.List.of(
                        new mchorse.bbs_ai.ui.ai.UIAIAssistMenu.Entry("推荐演员形态（分析 mod 环境）", () ->
                        {
                            mchorse.bbs_ai.integration.BBSAIClientIntegration.showSection("mods");
                        }),
                        new mchorse.bbs_ai.ui.ai.UIAIAssistMenu.Entry("打开 AI 编辑器", () ->
                        {
                            mchorse.bbs_ai.ui.editor.UIAIEditorPanel panel = dashboard.getPanel(mchorse.bbs_ai.ui.editor.UIAIEditorPanel.class);

                            if (panel != null)
                            {
                                dashboard.setPanel(panel);
                            }
                        })
                    )
                ),
                300,
                160
            );
        });
        this.aiAssist.tooltip(IKey.constant("AI 助手（演员形态推荐）"), Direction.TOP);

        this.palette.list.bar.add(this.aiAssist, this.fromMob, this.demorph);

        this.add(this.palette);

        this.controller = new ImmersiveMorphingCameraController(() -> this.palette.editor.isEditing() ? this.palette.editor.renderer : null);

        this.onAppear(this::enterMorphing);
        this.onDisappear(this::leaveMorphing);

        /* What the tour of this panel points at */
        TourAnchors.register("morphing.forms", () -> this.palette.list.forms);
        TourAnchors.register("morphing.edit", () -> this.palette.list.edit);
        TourAnchors.register("morphing.demorph", () -> this.demorph);
    }

    private void enterMorphing()
    {
        this.palette.list.forms.scroll.scrollSpeed = 40;

        Morph morph = ((IMorphProvider) MinecraftClient.getInstance().player).getMorph();

        this.palette.list.setupForms(BBSModClient.getFormCategories());
        this.palette.setSelected(morph.getForm());

        BBSModClient.getCameraController().add(this.controller);
        MinecraftClient.getInstance().options.setPerspective(Perspective.THIRD_PERSON_BACK);

        if (BBSSettings.morphingFocusSearch.get())
        {
            MinecraftClient.getInstance().execute(() -> this.palette.list.focusSearchInput());
        }
    }

    private void leaveMorphing()
    {
        BBSModClient.getCameraController().remove(this.controller);
        MinecraftClient.getInstance().options.setPerspective(Perspective.FIRST_PERSON);
    }

    private void setForm(Form form)
    {
        ClientNetwork.sendPlayerForm(form);
    }

    @Override
    public boolean needsBackground()
    {
        return !this.palette.editor.isEditing();
    }

}