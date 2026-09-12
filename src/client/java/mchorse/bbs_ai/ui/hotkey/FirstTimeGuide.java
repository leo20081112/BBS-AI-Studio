package mchorse.bbs_ai.ui.hotkey;

import org.lwjgl.glfw.GLFW;

import mchorse.bbs_ai.core.BBSAISettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * 首次使用引导
 *
 * <p>首次打开 AI 工具面板时显示浮动提示层：按顺序展示核心热键
 * N / T / G / R / S / I / 空格，每个提示停留 3 秒自动切换，
 * 用户可点击「跳过」。看过后写入设置不再弹出；
 * 随时可按 F1 打开热键速查表。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class FirstTimeGuide extends UIOverlayPanel
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
        super(IKey.constant("BBS AI Studio 快速入门"));

        this.stepStart = System.currentTimeMillis();

        UIButton skip = new UIButton(IKey.constant("跳过引导"), (b) -> this.close());

        skip.relative(this).x(0.5F, -40).y(1F, -34).w(80).h(20);

        this.add(skip);
        this.setInitialOffset(0, 0);
    }

    /**
     * 当前步骤的提示文本
     */
    private String currentText()
    {
        if (this.step < 0 || this.step >= STEPS.length)
        {
            return "";
        }

        HotkeyDefinition definition = HotkeyRegistry.get().get(STEPS[this.step]);

        if (definition == null)
        {
            return "";
        }

        return "第 " + (this.step + 1) + "/" + STEPS.length + " 步：按 "
            + KeyFormatter.format(definition) + " —— " + definition.description;
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
        }

        super.render(context);

        Batcher2D batcher = context.batcher;

        /* 提示条背景 + 居中白色文字 */
        String text = this.currentText();
        int textWidth = batcher.getFont().getWidth(text);
        int textHeight = batcher.getFont().getHeight();
        int x = this.area.mx(textWidth);
        int y = this.area.y - 24;

        batcher.box(x - 8, y - 4, x + textWidth + 8, y + textHeight + 4, 0x88000000);
        batcher.text(text, x, y, Colors.WHITE);
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
