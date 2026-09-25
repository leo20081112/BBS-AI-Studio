package mchorse.bbs_ai.ui.hotkey;

import org.lwjgl.glfw.GLFW;

import mchorse.bbs_ai.core.BBSAISettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;

/**
 * 首次使用引导
 *
 * <p>首次打开 AI 工具面板时弹出消息对话框：按顺序展示核心热键
 * N / T / G / R / S / I / 空格，每条提示停留 3 秒自动切换，
 * 用户可点击「跳过引导」。看过后写入设置不再弹出；
 * 随时可按 F1 打开热键速查表。</p>
 *
 * <p>继承 {@link UIMessageOverlayPanel}：步骤文案由面板正文的
 * 多行文本承载（bbs-fs 2.6 起空面板会以固定尺寸渲染成空白窗体，
 * 同时复用其按内容自适应高度的能力），避免出现大片空白。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class FirstTimeGuide extends UIMessageOverlayPanel
{
    /**
     * 每条提示的停留时长（毫秒）
     */
    private static final long STEP_DURATION_MS = 3000;

    /**
     * 引导步骤（热键 ID 顺序）
     */
    private static final String[] STEPS = {"panel.n_panel", "panel.toolbar", "transform.grab", "transform.rotate", "transform.scale", "keyframe.insert", "timeline.play"};

    /**
     * 当前步骤索引
     */
    private int step;

    /**
     * 当前步骤开始时间
     */
    private long stepStart;

    public FirstTimeGuide()
    {
        super(L10n.lang("bbs_ai.str.firstGuide.1"), IKey.EMPTY);

        UIButton skip = new UIButton(L10n.lang("bbs_ai.str.firstGuide.2"), (b) -> this.finish());

        /* 沿用 UIConfirmOverlayPanel 的习惯：按钮挂在正文下方并由 bottom 计入面板高度 */
        skip.relative(this.content).x(0.5F).y(1F, -10).w(80).anchor(0.5F, 1F);
        this.content.add(skip);
        this.bottom = skip;

        this.stepStart = System.currentTimeMillis();
        this.applyStep();
    }

    /**
     * 把当前步骤写进正文文本（换行交给 UIText 自动折行）
     */
    private void applyStep()
    {
        HotkeyDefinition definition = HotkeyRegistry.get().get(STEPS[this.step]);
        String key = definition == null ? "?" : KeyFormatter.format(definition);
        String description = definition == null ? "" : definition.description;

        this.message.text("第 " + (this.step + 1) + "/" + STEPS.length + " 步：按 " + key + " —— " + description);
    }

    @Override
    public void render(UIContext context)
    {
        /* 自动切换 */
        long elapsed = System.currentTimeMillis() - this.stepStart;

        if (elapsed >= STEP_DURATION_MS)
        {
            this.step++;
            this.stepStart = System.currentTimeMillis();

            if (this.step >= STEPS.length)
            {
                this.finish();

                return;
            }

            this.applyStep();
        }

        super.render(context);
    }

    /**
     * 结束引导：记录到设置
     */
    private void finish()
    {
        BBSAISettings.uiGuideSeen.set(true);
        this.close();
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        /* 按 Esc / 空格 直接跳过 */
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE) || context.isPressed(GLFW.GLFW_KEY_SPACE))
        {
            this.finish();

            return true;
        }

        return super.subKeyPressed(context);
    }
}
