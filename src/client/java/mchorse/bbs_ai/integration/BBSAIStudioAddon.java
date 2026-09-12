package mchorse.bbs_ai.integration;

import java.util.Collections;
import java.util.List;

import mchorse.bbs_ai.BBSAIStudio;
import mchorse.bbs_ai.ui.panel.UIAIToolsPanel;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.api.BBSAddonMod;
import mchorse.bbs_mod.api.Subscribe;
import mchorse.bbs_mod.api.client.events.RegisterL10nEvent;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.icons.Icons;

/**
 * BBS AI Studio 客户端 Addon
 *
 * <p>以 {@code bbs-client-addon} 入口点接入（在 BBSModClient 初始化最顶部注册），
 * 通过自定义 EventBus 的 {@code @Subscribe} 订阅 BBS 客户端事件【原版兼容】：
 * <ul>
 *   <li>{@link RegisterL10nEvent} —— 注册三语语言文件（重载前注册，保证首启即生效）</li>
 *   <li>{@code RegisterDashboardPanelsEvent} —— 注册「AI 工具」仪表盘面板</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BBSAIStudioAddon implements BBSAddonMod
{
    /**
     * 是否已注册面板（仪表盘可能多次创建）
     */
    private boolean panelsRegistered;

    /**
     * 注册语言文件（BBS 语言重载前调用）【原版兼容】
     */
    @Subscribe
    public void onRegisterL10n(RegisterL10nEvent event)
    {
        L10n l10n = event.l10n;

        l10n.register((lang) ->
        {
            List<Link> links = Collections.singletonList(Link.assets("strings/bbs_ai_" + lang + ".json"));

            return links;
        });

        System.out.println("[BBS AI] 语言文件已注册（bbs_ai_<lang>.json）");
    }

    /**
     * 注册仪表盘面板
     */
    @Subscribe
    public void onRegisterDashboardPanels(mchorse.bbs_mod.api.client.events.RegisterDashboardPanelsEvent event)
    {
        if (this.panelsRegistered)
        {
            return;
        }

        this.panelsRegistered = true;

        event.dashboard.getPanels().registerPanel(
            new UIAIToolsPanel(event.dashboard),
            IKey.constant("BBS AI Studio（" + BBSAIStudio.VERSION + "）"),
            Icons.PROCESSOR
        );
    }
}
