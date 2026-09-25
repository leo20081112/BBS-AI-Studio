package mchorse.bbs_ai.ui.editor;

import mchorse.bbs_ai.compat.IModAdapter;
import mchorse.bbs_ai.compat.ModAdapterManager;
import mchorse.bbs_ai.core.AIConfig;
import mchorse.bbs_ai.core.AIServiceManager;
import mchorse.bbs_ai.core.BBSAISettings;
import mchorse.bbs_ai.core.api.APIException;
import mchorse.bbs_ai.mods.AIContextService;
import mchorse.bbs_ai.mods.ModCompatScanner;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * AI 设置界面（从 AI 编辑器进入）
 *
 * <p>四大区块（UISection 折叠卡片，原生观感）：
 * <ul>
 *   <li>AI 服务 —— 厂商 / API Key（掩码）/ Base URL / 模型 / 测试连接</li>
 *   <li>生成参数 —— 采样温度 / 最大 Token（写入加密配置）</li>
 *   <li>人物模型 —— 当前模型信息 / 骨骼映射 / 模型浏览器 / 导出</li>
 *   <li>插件调用 —— mod 环境注入开关 / 各适配器独立开关（持久化）/ 重新扫描</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class AISettingsOverlayPanel extends UIOverlayPanel
{
    /* ---- AI 服务 ---- */
    private UICirculate providerCirculate;
    private UITextbox apiKeyBox;
    private boolean showApiKey;
    private UITextbox baseUrlBox;
    private UITextbox modelBox;
    private UILabel testResult;

    /* ---- 生成参数 ---- */
    private UITrackpad temperature;
    private UITrackpad maxTokens;

    /* ---- 人物模型 ---- */
    private UILabel modelInfo;

    public AISettingsOverlayPanel()
    {
        super(L10n.lang("bbs_ai.settings.title"));

        AIConfig config = AIServiceManager.get().getConfig();

        /* ============ AI 服务 ============ */
        UISection service = new UISection(L10n.lang("bbs_ai.settings.service"));

        this.providerCirculate = new UICirculate((c) -> this.applyProvider(c.getValue()));

        for (AIConfig.Provider provider : AIConfig.Provider.values())
        {
            this.providerCirculate.addLabel(IKey.constant(provider.title));
        }

        this.providerCirculate.setValue(config.getProvider().ordinal());

        this.apiKeyBox = new UITextbox(4096, (t) -> this.pushConfig());
        this.apiKeyBox.setText(this.showApiKey ? config.getApiKey() : this.maskKey(config.getApiKey()));

        UIIcon show = new UIIcon(Icons.INVISIBLE, (b) -> this.toggleShowKey());
        show.tooltip(IKey.constant("显示/隐藏 API Key"));

        this.baseUrlBox = new UITextbox(2048, (t) -> this.pushConfig());
        this.baseUrlBox.setText(config.getBaseUrl());

        this.modelBox = new UITextbox(512, (t) -> this.pushConfig());
        this.modelBox.setText(config.getModel());

        UIButton test = new UIButton(L10n.lang("bbs_ai.panel.settings.test"), (b) -> this.testConnection());

        this.testResult = UI.label(IKey.constant(" "), 14, Colors.GRAY);

        service.fields.add(
            this.providerCirculate,
            UI.labelRow(IKey.constant("API Key"), this.apiKeyBox),
            show,
            UI.labelRow(IKey.constant("Base URL"), this.baseUrlBox),
            UI.labelRow(IKey.constant("模型"), this.modelBox),
            test,
            this.testResult
        );

        /* ============ 生成参数 ============ */
        UISection generate = new UISection(L10n.lang("bbs_ai.settings.generate"));

        this.temperature = new UITrackpad((v) -> BBSAISettings.aiTemperature.set(v.floatValue()));
        this.temperature.limit(0F, 2F).increment(0.05F);
        this.temperature.setValue(BBSAISettings.aiTemperature.get().doubleValue());

        this.maxTokens = new UITrackpad((v) -> BBSAISettings.aiMaxTokens.set(v.intValue()));
        this.maxTokens.limit(256, 8192, true);
        this.maxTokens.setValue(BBSAISettings.aiMaxTokens.get());

        generate.fields.add(
            UI.labelRow(L10n.lang("bbs_ai.settings.generate.temperature"), this.temperature),
            UI.labelRow(L10n.lang("bbs_ai.settings.generate.max_tokens"), this.maxTokens)
        );

        /* ============ 人物模型 ============ */
        UISection model = new UISection(L10n.lang("bbs_ai.settings.model"));

        ModelForm form = mchorse.bbs_ai.ui.model.ModelFormUI.resolveCurrentModelForm();

        this.modelInfo = UI.label(IKey.constant(form == null
            ? L10n.lang("bbs_ai.panel.model.no_form").get()
            : L10n.lang("bbs_ai.panel.model.current").format(form.model.get()).get()), 14, Colors.GRAY);

        UIButton mapping = new UIButton(L10n.lang("bbs_ai.settings.model.mapping"), (b) ->
        {
            ModelForm current = mchorse.bbs_ai.ui.model.ModelFormUI.resolveCurrentModelForm();

            if (current == null)
            {
                return;
            }

            UIOverlay.addOverlay(this.getContext(), new mchorse.bbs_ai.ui.model.UIBoneMappingPanel(current, null), 340, 320);
        });

        UIButton browser = new UIButton(L10n.lang("bbs_ai.settings.model.browser"), (b) ->
        {
            UIOverlay.addOverlay(this.getContext(), new mchorse.bbs_ai.ui.model.ModelBrowserPanel(), 380, 340);
        });

        UIButton export = new UIButton(L10n.lang("bbs_ai.settings.model.export"), (b) ->
        {
            UIOverlay.addOverlay(this.getContext(), new mchorse.bbs_ai.ui.model.ExportModelPanel(), 340, 300);
        });

        model.fields.add(this.modelInfo, mapping, browser, export);

        /* ============ 插件调用 ============ */
        UISection plugins = new UISection(L10n.lang("bbs_ai.settings.plugins"));

        UIToggle contextToggle = new UIToggle(L10n.lang("bbs_ai.settings.plugins.context"), BBSAISettings.pluginsContextEnabled.get(), (b) ->
        {
            BBSAISettings.pluginsContextEnabled.set(b.getValue());
        });

        plugins.fields.add(contextToggle, UI.label(L10n.lang("bbs_ai.settings.plugins.adapters"), 14, Colors.GRAY));

        for (IModAdapter adapter : ModAdapterManager.getAdapters())
        {
            String id = ModAdapterManager.getAdapterId(adapter);
            UIToggle toggle = new UIToggle(IKey.constant(adapter.title() + "（" + id + "）"), ModAdapterManager.isEnabled(adapter), (b) ->
            {
                ModAdapterManager.setEnabled(adapter, b.getValue());
                BBSAISettings.pluginsAdaptersDisabled.set(ModAdapterManager.exportDisabledList());
            });

            plugins.fields.add(toggle);
        }

        UIButton rescan = new UIButton(L10n.lang("bbs_ai.panel.mods.rescan"), (b) ->
        {
            ModCompatScanner.get().rescan();
            AIContextService.get().invalidate();
            this.getContext().notifyInfo(L10n.lang("bbs_ai.settings.plugins.rescanned"));
        });

        plugins.fields.add(rescan);

        /* ============ 组装 ============ */
        UIScrollView scroll = UI.scrollView(service, generate, model, plugins);

        scroll.relative(this.content).xy(0, 0).w(1F).h(1F);
        this.content.add(scroll);
    }

    /* ====================================================================
     * AI 服务（与工具面板一致的双向绑定逻辑）
     * ==================================================================== */

    private void pushConfig()
    {
        AIConfig config = AIServiceManager.get().getConfig();
        String key = this.apiKeyBox.getText();

        if (!this.showApiKey && key.contains("•"))
        {
            key = config.getApiKey();
        }

        config.setApiKey(key);
        config.setBaseUrl(this.baseUrlBox.getText().trim());
        config.setModel(this.modelBox.getText().trim());

        AIServiceManager.get().updateConfig(config);
    }

    private void applyProvider(int index)
    {
        AIConfig.Provider provider = AIConfig.Provider.values()[Math.max(0, Math.min(AIConfig.Provider.values().length - 1, index))];
        AIConfig config = AIServiceManager.get().getConfig();

        config.applyProvider(provider);
        AIServiceManager.get().updateConfig(config);

        this.baseUrlBox.setText(config.getBaseUrl());
        this.modelBox.setText(config.getModel());
    }

    private void toggleShowKey()
    {
        AIConfig config = AIServiceManager.get().getConfig();

        this.showApiKey = !this.showApiKey;

        if (this.showApiKey)
        {
            this.apiKeyBox.setText(config.getApiKey());
        }
        else
        {
            String current = this.apiKeyBox.getText();

            if (!current.contains("•"))
            {
                this.pushConfig();
            }

            this.apiKeyBox.setText(this.maskKey(config.getApiKey()));
        }
    }

    private String maskKey(String key)
    {
        if (key == null || key.isEmpty())
        {
            return "";
        }

        int visible = Math.min(4, key.length());

        return key.substring(0, visible) + "••••••••";
    }

    private void testConnection()
    {
        this.testResult.label = IKey.constant("测试中...");
        this.testResult.color(Colors.GRAY);

        AIServiceManager.get().testConnectionAsync(
            (ok) -> this.testResult.label = IKey.constant("√ 连接成功"),
            (APIException error) -> this.testResult.label = IKey.constant("X " + error.getUserMessage())
        );
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);
    }
}
