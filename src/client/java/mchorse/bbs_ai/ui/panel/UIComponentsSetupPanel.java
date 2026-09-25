package mchorse.bbs_ai.ui.panel;

import mchorse.bbs_ai.core.BBSAISettings;
import mchorse.bbs_ai.core.LocalComponentsDownloader;
import mchorse.bbs_ai.core.LocalComponentsDownloader.Component;
import mchorse.bbs_ai.core.LocalComponentsDownloader.DownloadJob;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UICanvas;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.List;

/**
 * 本地组件安装询问 / 进度面板
 *
 * <p>进入世界后检测到视频识别依赖缺失（FFmpeg 或 ONNX 姿态模型）时弹出，
 * 经用户确认后调用 {@link LocalComponentsDownloader#download(List)} 在后台
 * 逐组件下载（SHA-256 校验），面板每帧轮询 {@link DownloadJob} 的易变字段
 * 刷新进度条；下载在后台继续，即使面板被关闭。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class UIComponentsSetupPanel extends UIOverlayPanel
{
    /**
     * 缺失组件清单
     */
    private final List<Component> components;

    /**
     * 状态行（下载进度 / 结果 / 错误）
     */
    private final UILabel statusLabel;

    /**
     * 进度条画布
     */
    private final ProgressBar bar;

    /**
     * 下载按钮（开始后隐藏）
     */
    private final UIButton downloadButton;

    /**
     * "不再提示"按钮（开始后隐藏）
     */
    private final UIButton disableButton;

    /**
     * 进行中的下载任务（null = 未开始）
     */
    private DownloadJob job;

    /**
     * 状态行缓存（避免每帧重建字符串）
     */
    private String lastStatus = "";

    public UIComponentsSetupPanel(List<Component> components)
    {
        super(L10n.lang("bbs_ai.panel.components.title"));

        this.components = components;

        int margin = 10;
        int y = 6;

        UILabel hint = UI.label(L10n.lang("bbs_ai.panel.components.hint"), 12, Colors.GRAY);

        hint.relative(this.content).x(margin).y(y).w(1F, -margin * 2).h(26);
        this.content.add(hint);
        y += 30;

        /* 每个组件一行（UILabel 单行渲染，逐行列出名称与大小） */
        for (Component component : this.components)
        {
            UILabel row = UI.label(IKey.constant(
                "• " + component.title + "  (" + LocalComponentsDownloader.formatSize(component.downloadSize) + ")"), 13, Colors.WHITE);

            row.relative(this.content).x(margin).y(y).w(1F, -margin * 2).h(16);
            this.content.add(row);
            y += 18;
        }

        y += 2;

        this.bar = new ProgressBar();

        this.bar.relative(this.content).x(margin).y(y).w(1F, -margin * 2).h(10);
        this.bar.setVisible(false);
        this.content.add(this.bar);

        this.statusLabel = UI.label(IKey.constant(" "), 12, Colors.GRAY);

        this.statusLabel.relative(this.content).x(margin).y(y + 14).w(1F, -margin * 2);
        this.content.add(this.statusLabel);
        y += 36;

        this.downloadButton = new UIButton(L10n.lang("bbs_ai.panel.components.download"), (b) -> this.startDownload());

        this.downloadButton.relative(this.content).x(margin).y(1F, -32).w(1F, -margin * 2 - 100).h(22);
        this.content.add(this.downloadButton);

        this.disableButton = new UIButton(L10n.lang("bbs_ai.panel.components.disable"), (b) -> this.disablePrompt());

        this.disableButton.relative(this.content).x(1F, -margin - 94).y(1F, -32).w(94).h(22);
        this.content.add(this.disableButton);
    }

    /**
     * 开始后台下载，切换面板到进度态
     */
    private void startDownload()
    {
        if (this.jobStarted())
        {
            return;
        }

        this.job = LocalComponentsDownloader.download(this.components);
        this.downloadButton.removeFromParent();
        this.disableButton.removeFromParent();
        this.bar.setVisible(true);
        this.content.resize();
    }

    /**
     * 是否已启动下载
     */
    private boolean jobStarted()
    {
        return this.job != null;
    }

    /**
     * "不再提示"：写入设置并关闭
     */
    private void disablePrompt()
    {
        if (BBSAISettings.componentsPromptDisabled != null)
        {
            BBSAISettings.componentsPromptDisabled.set(true);
        }

        this.close();
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        if (!this.jobStarted())
        {
            return;
        }

        DownloadJob job = this.job;
        String status;

        if (job.finished)
        {
            if (job.success)
            {
                status = L10n.lang("bbs_ai.panel.components.done").get();

                if (!this.lastStatus.equals(status))
                {
                    this.getContext().notifySuccess(IKey.constant(status));
                    this.close();
                }
            }
            else
            {
                status = L10n.lang("bbs_ai.panel.components.failed").get() + " " + job.error;
            }
        }
        else
        {
            status = job.currentTitle + " — " + job.statusLine;
        }

        if (!status.equals(this.lastStatus))
        {
            this.lastStatus = status;
            this.statusLabel.label = IKey.constant(status);
        }

        this.bar.progress = job.percent / 100.0F;
    }

    /**
     * 简易进度条（背景条 + 填充条）
     */
    private static class ProgressBar extends UICanvas
    {
        /**
         * 进度 0 ~ 1（渲染线程每帧从 DownloadJob 同步）
         */
        public float progress;

        @Override
        public void render(UIContext context)
        {
            super.render(context);

            int x = this.area.x;
            int y = this.area.y;
            int w = this.area.w;
            int h = this.area.h;

            context.batcher.box(x, y, x + w, y + h, 0x66000000);

            int fill = (int) (w * Math.max(0.0F, Math.min(1.0F, this.progress)));

            if (fill > 0)
            {
                context.batcher.box(x, y, x + fill, y + h, 0xFF4CAF50);
            }
        }
    }
}
