package mchorse.bbs_ai.ui.panel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import mchorse.bbs_ai.core.AIConfig;
import mchorse.bbs_ai.core.AIServiceManager;
import mchorse.bbs_ai.core.BBSAISettings;
import mchorse.bbs_ai.core.api.APIException;
import mchorse.bbs_ai.ik.BlenderIKComponent;
import mchorse.bbs_ai.ik.BlenderIKGizmo;
import mchorse.bbs_ai.ik.BlenderIKSettingsPanel;
import mchorse.bbs_ai.ik.IKBone;
import mchorse.bbs_ai.ik.IKBoneChain;
import mchorse.bbs_ai.import_manager.ImportEntry;
import mchorse.bbs_ai.import_manager.ImportManager;
import mchorse.bbs_ai.motion.VideoFrameExtractor;
import mchorse.bbs_ai.motion.VideoRecognitionPipeline;
import mchorse.bbs_ai.preview.BakeConfirmationDialog;
import mchorse.bbs_ai.preview.PreviewRenderer;
import mchorse.bbs_ai.preview.PreviewSystem;
import mchorse.bbs_ai.preview.PreviewTrack;
import mchorse.bbs_ai.storyboard.StoryboardPromptBuilder;
import mchorse.bbs_ai.storyboard.StoryboardScript;
import mchorse.bbs_ai.storyboard.StoryboardToFilmConverter;
import mchorse.bbs_ai.ui.hotkey.FirstTimeGuide;
import mchorse.bbs_ai.ui.hotkey.HotkeySettingsPanel;
import mchorse.bbs_ai.ui.hotkey.StatusBar;
import mchorse.bbs_ai.ui.language.LanguageManager;
import mchorse.bbs_ai.ui.theme.ThemeManager;
import mchorse.bbs_ai.ui.theme.UITheme;
import mchorse.bbs_ai.ui.transform.KeyBindingOverrideManager;
import mchorse.bbs_ai.ui.transform.OperationMode;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * BBS AI Studio 主面板（仪表盘面板）
 *
 * <p>六个功能区（顶部图标切换）：导入管理 / 视频识别 / 分镜生成 / AI 设置 / IK 调整 / 界面，
 * 底部为快捷键状态栏。数据流遵循项目规范：所有生成结果先进预览系统，
 * 经 BakeConfirmationDialog 确认后才写入正式 Film【原版兼容】。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class UIAIToolsPanel extends UIDashboardPanel
{
    /* ---- 区块切换 ---- */
    private final UIElement sectionHost = new UIElement();
    private UIElement currentSection;

    /* ---- 导入管理 ---- */
    private UIStringList importList;
    private List<ImportEntry> entries = new ArrayList<>();
    private UILabel importInfo;

    /* ---- 视频识别 ---- */
    private UITextbox videoPath;
    private UIToggle mirrorToggle;
    private UILabel recognizeProgress;
    private final VideoRecognitionPipeline pipeline = new VideoRecognitionPipeline();

    /* ---- 分镜生成 ---- */
    private UITextarea storyboardPrompt;
    private UILabel storyboardStatus;

    /* ---- AI 设置 ---- */
    private UICirculate providerCirculate;
    private UITextbox apiKeyBox;
    private boolean showApiKey;
    private UITextbox baseUrlBox;
    private UITextbox modelBox;
    private UILabel testResult;

    /* ---- IK ---- */
    private final BlenderIKComponent ikComponent;
    private final BlenderIKGizmo gizmo;

    /* ---- 界面 ---- */
    private UICirculate themeCirculate;
    private UICirculate languageCirculate;
    private UICirculate modeCirculate;

    /* ---- 状态栏 ---- */
    private final StatusBar statusBar = new StatusBar();

    public UIAIToolsPanel(UIDashboard dashboard)
    {
        super(dashboard);

        /* 顶部区块切换图标 */
        UIElement topBar = UI.row(2,
            this.icon(Icons.DOWNLOAD, "bbs_ai.panel.tab.import", "import"),
            this.icon(Icons.VIDEO_CAMERA, "bbs_ai.panel.tab.video", "video"),
            this.icon(Icons.FONT, "bbs_ai.panel.tab.storyboard", "storyboard"),
            this.icon(Icons.PROCESSOR, "bbs_ai.panel.tab.settings", "settings"),
            this.icon(Icons.IK, "bbs_ai.panel.tab.ik", "ik"),
            this.icon(Icons.LAYOUT, "bbs_ai.panel.tab.interface", "interface")
        );

        topBar.relative(this).xy(0, 0).w(1F).h(24);

        this.sectionHost.relative(this).xy(0, 24).w(1F).h(1F, -40);
        this.statusBar.relative(this).y(1F, -16).w(1F).h(16);

        this.add(topBar, this.sectionHost, this.statusBar);

        /* IK 组件（head 链演示） */
        this.ikComponent = new BlenderIKComponent("ik_head", this.buildDemoChain());
        this.gizmo = new BlenderIKGizmo(this.ikComponent);
        this.gizmo.setOnSolve(this::solveIK);

        this.showSection("import");
    }

    /**
     * 构建顶部切换图标
     */
    private UIIcon icon(Icon icon, String tooltipKey, String section)
    {
        UIIcon button = new UIIcon(icon, (b) -> this.showSection(section));

        button.tooltip(L10n.lang(tooltipKey));

        return button;
    }

    /**
     * 切换功能区块
     */
    public void showSection(String id)
    {
        if (this.currentSection != null)
        {
            this.currentSection.removeFromParent();
        }

        this.currentSection = this.buildSection(id);

        this.currentSection.relative(this.sectionHost).xy(0, 0).w(1F).h(1F);
        this.sectionHost.add(this.currentSection);
    }

    /**
     * 构建指定区块
     */
    private UIElement buildSection(String id)
    {
        switch (id)
        {
            case "video": return this.buildVideoSection();
            case "storyboard": return this.buildStoryboardSection();
            case "settings": return this.buildSettingsSection();
            case "ik": return this.buildIKSection();
            case "interface": return this.buildInterfaceSection();
            default: return this.buildImportSection();
        }
    }

    /* ====================================================================
     * 区块一：导入管理
     * ==================================================================== */

    private UIElement buildImportSection()
    {
        UIElement section = new UIElement();

        this.importList = new UIStringList((l) -> this.updateImportInfo());
        this.importList.relative(section).xy(0, 0).w(1F).h(1F, -60);
        this.importList.background();

        UIButton refresh = new UIButton(L10n.lang("bbs_ai.panel.import.refresh"), (b) ->
        {
            ImportManager.get().scanAllSources();
            this.refreshImports();
        });

        UIButton preview = new UIButton(L10n.lang("bbs_ai.panel.import.preview"), (b) -> this.previewSelectedImport());
        UIButton bake = new UIButton(L10n.lang("bbs_ai.panel.import.bake"), (b) -> this.bakeSelectedImport());

        UIElement buttons = UI.row(4, refresh, preview, bake);

        buttons.relative(section).y(1F, -56).w(1F).h(22);

        this.importInfo = UI.label(L10n.lang("bbs_ai.panel.import.empty"), 14, Colors.GRAY);
        this.importInfo.relative(section).x(6).y(1F, -30).w(1F, -12);

        section.add(this.importList, buttons, this.importInfo);

        this.refreshImports();

        return section;
    }

    /**
     * 刷新导入列表
     */
    private void refreshImports()
    {
        if (this.importList == null)
        {
            return;
        }

        this.entries = ImportManager.get().getEntries();
        this.importList.clear();

        for (ImportEntry entry : this.entries)
        {
            String badge = entry.source == mchorse.bbs_ai.import_manager.SourceType.INTERNAL_AI ? "[AI]" : "[外部]";

            this.importList.add(badge + " " + entry.name + (entry.frames > 0 ? "（" + entry.frames + "帧）" : ""));
        }
    }

    /**
     * 更新选中条目详细信息
     */
    private void updateImportInfo()
    {
        if (this.importInfo == null)
        {
            return;
        }

        ImportEntry entry = this.getSelectedEntry();

        if (entry == null)
        {
            this.importInfo.label = L10n.lang("bbs_ai.panel.import.empty");

            return;
        }

        String duration = entry.duration > 0 ? String.format("%.1f", entry.duration) + "秒" : "未知时长";

        this.importInfo.label = IKey.constant(entry.source.getTitle() + " | " + duration
            + " | 模型: " + (entry.model.isEmpty() ? "未知" : entry.model));
    }

    /**
     * 获取选中条目
     */
    private ImportEntry getSelectedEntry()
    {
        int index = this.importList == null ? -1 : this.importList.getIndex();

        return index >= 0 && index < this.entries.size() ? this.entries.get(index) : null;
    }

    /**
     * 预览选中的动作数据（进入预览系统）
     */
    private void previewSelectedImport()
    {
        ImportEntry entry = this.getSelectedEntry();

        if (entry == null)
        {
            this.notify("请先选择一个动作数据");

            return;
        }

        try
        {
            mchorse.bbs_ai.format.MotionData data = mchorse.bbs_ai.format.MotionData.readFromFile(entry.file);

            PreviewSystem.get().stage("", this.getSelectedReplayId(), "", entry.source, data.keyframes);

            this.notify("已进入预览（" + data.keyframes.size() + " 帧），确认后烘焙");
        }
        catch (Exception e)
        {
            this.notify("读取失败：" + e.getMessage());
        }
    }

    /**
     * 打开烘焙确认对话框
     */
    private void bakeSelectedImport()
    {
        String key = "|" + this.getSelectedReplayId() + "|";
        PreviewTrack track = PreviewSystem.get().getCache().get(key);

        if (track == null)
        {
            this.notify("请先点击「预览」");

            return;
        }

        mchorse.bbs_ai.preview.PreviewContext context = PreviewSystem.get().getCache().getContext(key);

        UIOverlay.addOverlay(this.getContext(), new BakeConfirmationDialog(context, track), 320, 220);
    }

    /* ====================================================================
     * 区块二：视频识别
     * ==================================================================== */

    private UIElement buildVideoSection()
    {
        UIElement section = new UIElement();
        int y = 8;

        UILabel titleLabel = UI.label(L10n.lang("bbs_ai.panel.video.title"), 14, 0xAAAAAA);

        titleLabel.relative(section).xy(8, y);
        section.add(titleLabel);
        y += 22;

        this.videoPath = new UITextbox(4096, (t) ->
        {});

        this.videoPath.relative(section).xy(8, y).w(1F, -16).h(20);
        this.videoPath.placeholder(L10n.lang("bbs_ai.panel.video.path"));
        section.add(this.videoPath);
        y += 28;

        this.mirrorToggle = new UIToggle(L10n.lang("bbs_ai.panel.video.mirror"), false, (b) ->
        {});

        this.mirrorToggle.relative(section).xy(8, y).w(1F, -16).h(18);
        section.add(this.mirrorToggle);
        y += 26;

        UIButton start = new UIButton(L10n.lang("bbs_ai.panel.video.start"), (b) -> this.startRecognition());

        start.relative(section).xy(8, y).w(1F, -16).h(22);
        section.add(start);
        y += 30;

        this.recognizeProgress = UI.label(L10n.lang("bbs_ai.panel.video.hint"), 14, Colors.GRAY);
        this.recognizeProgress.relative(section).xy(8, y).w(1F, -16);
        section.add(this.recognizeProgress);

        return section;
    }

    /**
     * 启动识别流水线
     */
    private void startRecognition()
    {
        String path = this.videoPath.getText().trim();

        if (path.isEmpty())
        {
            this.notify("请先填写视频文件路径");

            return;
        }

        File video = new File(path);

        if (!VideoFrameExtractor.isSupportedVideo(video))
        {
            this.notify("视频不存在或格式不支持");

            return;
        }

        if (this.pipeline.isRunning())
        {
            this.notify("识别任务进行中，请稍候");

            return;
        }

        this.recognizeProgress.label = IKey.constant("识别中... 0%");

        this.pipeline.start(video,
            BBSAISettings.motionSampleRate.get(),
            this.mirrorToggle.getValue(),
            "",
            this.getSelectedReplayId(),
            (p) -> this.recognizeProgress.label = IKey.constant(String.format("识别中... %d%%", (int) (p * 100))),
            (file) -> this.recognizeProgress.label = IKey.constant("完成！输出：" + file.getName()),
            (error) -> this.recognizeProgress.label = IKey.constant("失败：" + error));
    }

    /* ====================================================================
     * 区块三：分镜生成
     * ==================================================================== */

    private UIElement buildStoryboardSection()
    {
        UIElement section = new UIElement();
        int y = 8;

        UILabel titleLabel = UI.label(L10n.lang("bbs_ai.panel.storyboard.title"), 14, 0xAAAAAA);

        titleLabel.relative(section).xy(8, y);
        section.add(titleLabel);
        y += 22;

        this.storyboardPrompt = new UITextarea((t) ->
        {});

        this.storyboardPrompt.background().wrap();
        this.storyboardPrompt.relative(section).xy(8, y).w(1F, -16).h(1F, -130);
        section.add(this.storyboardPrompt);

        UIButton generate = new UIButton(L10n.lang("bbs_ai.panel.storyboard.generate"), (b) -> this.generateStoryboard());

        generate.relative(section).x(8).y(1F, -66).w(1F, -16).h(22);
        section.add(generate);

        this.storyboardStatus = UI.label(IKey.constant(""), 14, Colors.GRAY);
        this.storyboardStatus.relative(section).x(8).y(1F, -38).w(1F, -16);
        section.add(this.storyboardStatus);

        return section;
    }

    /**
     * 生成分镜
     */
    private void generateStoryboard()
    {
        String prompt = this.storyboardPrompt.getText().trim();

        if (prompt.isEmpty())
        {
            this.notify("请先输入剧情描述");

            return;
        }

        AIServiceManager service = AIServiceManager.get();

        if (!service.isApiMode())
        {
            this.storyboardStatus.label = IKey.constant("需要 API 模式（AI 设置中配置）");

            return;
        }

        this.storyboardStatus.label = IKey.constant("生成中...");

        service.generateAsync(
            StoryboardPromptBuilder.buildSystemPrompt(),
            StoryboardPromptBuilder.buildUserPrompt(prompt, 10),
            null,
            (result) ->
            {
                try
                {
                    StoryboardScript script = StoryboardScript.fromJson(result);
                    Film film = new StoryboardToFilmConverter().convert(script);
                    String name = "ai_storyboard_" + System.currentTimeMillis() / 1000;

                    BBSMod.getFilms().create(name, (MapType) film.toData());

                    this.storyboardStatus.label = IKey.constant("已生成影片：" + name + "（" + script.shots.size() + " 镜头）");
                }
                catch (Exception e)
                {
                    this.storyboardStatus.label = IKey.constant("解析失败：" + e.getMessage());
                }
            },
            (APIException error) -> this.storyboardStatus.label = IKey.constant(error.getUserMessage())
        );
    }

    /* ====================================================================
     * 区块四：AI 设置
     * ==================================================================== */

    private UIElement buildSettingsSection()
    {
        UIElement section = new UIElement();
        int y = 8;

        AIConfig config = AIServiceManager.get().getConfig();

        UILabel titleLabel = UI.label(L10n.lang("bbs_ai.panel.settings.title"), 14, 0xAAAAAA);

        titleLabel.relative(section).xy(8, y);
        section.add(titleLabel);
        y += 22;

        this.providerCirculate = new UICirculate((c) -> this.applyProvider(c.getValue()));

        for (AIConfig.Provider provider : AIConfig.Provider.values())
        {
            this.providerCirculate.addLabel(IKey.constant(provider.title));
        }

        this.providerCirculate.setValue(config.getProvider().ordinal());
        this.providerCirculate.relative(section).xy(8, y).w(1F, -16).h(20);
        section.add(this.providerCirculate);
        y += 26;

        /* API Key（掩码 + 显示切换） */
        this.apiKeyBox = new UITextbox(4096, (t) -> this.pushConfig());

        this.apiKeyBox.setText(this.showApiKey ? config.getApiKey() : this.maskKey(config.getApiKey()));
        this.apiKeyBox.relative(section).xy(8, y).w(1F, -56).h(20);
        section.add(this.apiKeyBox);

        UIIcon show = new UIIcon(this.showApiKey ? Icons.INVISIBLE : Icons.VISIBLE, (b) -> this.toggleShowKey());

        show.relative(section).x(1F, -46).y(y).w(20).h(20);
        show.tooltip(IKey.constant("显示/隐藏 API Key"));
        section.add(show);
        y += 26;

        this.baseUrlBox = new UITextbox(2048, (t) -> this.pushConfig());

        this.baseUrlBox.setText(config.getBaseUrl());
        this.baseUrlBox.relative(section).xy(8, y).w(1F, -16).h(20);
        section.add(this.baseUrlBox);
        y += 26;

        this.modelBox = new UITextbox(512, (t) -> this.pushConfig());

        this.modelBox.setText(config.getModel());
        this.modelBox.relative(section).xy(8, y).w(1F, -16).h(20);
        section.add(this.modelBox);
        y += 28;

        UIButton test = new UIButton(L10n.lang("bbs_ai.panel.settings.test"), (b) -> this.testConnection());

        test.relative(section).xy(8, y).w(1F, -16).h(22);
        section.add(test);
        y += 30;

        this.testResult = UI.label(IKey.constant(" "), 14, Colors.GRAY);
        this.testResult.relative(section).xy(8, y).w(1F, -16);
        section.add(this.testResult);

        return section;
    }

    /**
     * 把面板输入写回 AI 配置（加密持久化）
     */
    private void pushConfig()
    {
        AIConfig config = AIServiceManager.get().getConfig();
        String key = this.apiKeyBox.getText();

        /* 掩码状态下不覆盖真实 Key */
        if (!this.showApiKey && key.contains("•"))
        {
            key = config.getApiKey();
        }

        config.setApiKey(key);
        config.setBaseUrl(this.baseUrlBox.getText().trim());
        config.setModel(this.modelBox.getText().trim());

        AIServiceManager.get().updateConfig(config);
    }

    /**
     * 应用厂商预设
     */
    private void applyProvider(int index)
    {
        AIConfig.Provider provider = AIConfig.Provider.values()[Math.max(0, Math.min(AIConfig.Provider.values().length - 1, index))];
        AIConfig config = AIServiceManager.get().getConfig();

        config.applyProvider(provider);
        AIServiceManager.get().updateConfig(config);

        if (this.baseUrlBox != null)
        {
            this.baseUrlBox.setText(config.getBaseUrl());
            this.modelBox.setText(config.getModel());
        }
    }

    /**
     * 显示/隐藏 API Key
     */
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

    /**
     * 掩码 API Key
     */
    private String maskKey(String key)
    {
        if (key == null || key.isEmpty())
        {
            return "";
        }

        int visible = Math.min(4, key.length());

        return key.substring(0, visible) + "••••••••";
    }

    /**
     * 测试连接
     */
    private void testConnection()
    {
        this.testResult.label = IKey.constant("测试中...");
        this.testResult.color(Colors.GRAY);

        AIServiceManager.get().testConnectionAsync(
            (ok) -> this.testResult.label = IKey.constant("√ 连接成功"),
            (APIException error) -> this.testResult.label = IKey.constant("X " + error.getUserMessage())
        );
    }

    /* ====================================================================
     * 区块五：IK 调整
     * ==================================================================== */

    private UIElement buildIKSection()
    {
        UIElement section = new UIElement();
        int y = 8;

        UILabel titleLabel = UI.label(L10n.lang("bbs_ai.panel.ik.title"), 14, 0xAAAAAA);

        titleLabel.relative(section).xy(8, y);
        section.add(titleLabel);
        y += 22;

        UILabel hint = UI.label(IKey.constant("拖拽黄色目标移动末端骨骼；蓝色点控制弯曲方向（Shift 调高度）"), 14, Colors.GRAY);
        hint.relative(section).xy(8, y).w(1F, -16);
        section.add(hint);
        y += 24;

        UIButton openPanel = new UIButton(IKey.constant("打开约束面板"), (b) ->
        {
            UIOverlay.addOverlay(this.getContext(), new BlenderIKSettingsPanel(this.ikComponent, this::solveIK), 300, 440);
        });

        openPanel.relative(section).xy(8, y).w(1F, -16).h(22);
        section.add(openPanel);
        y += 28;

        UIButton solve = new UIButton(IKey.constant("解算并预览"), (b) -> this.solveIK());

        solve.relative(section).xy(8, y).w(1F, -16).h(22);
        section.add(solve);
        y += 28;

        UIButton bake = new UIButton(IKey.constant("烘焙 IK 结果..."), (b) -> this.bakeSelectedImport());

        bake.relative(section).xy(8, y).w(1F, -16).h(22);
        section.add(bake);

        return section;
    }

    /**
     * 构建演示骨骼链（末端 → 根部）
     */
    private IKBoneChain buildDemoChain()
    {
        IKBoneChain chain = new IKBoneChain("ik_demo");

        IKBone head = new IKBone("head");

        head.setPositions(new org.joml.Vector3f(0.0F, 1.4F, 0.0F), new org.joml.Vector3f(0.0F, 1.9F, 0.0F));
        chain.addBone(head);

        IKBone body = new IKBone("body");

        body.setPositions(new org.joml.Vector3f(0.0F, 0.7F, 0.0F), new org.joml.Vector3f(0.0F, 1.4F, 0.0F));
        chain.addBone(body);

        return chain;
    }

    /**
     * 执行 IK 解算并进入预览
     */
    private void solveIK()
    {
        this.ikComponent.atTick = 0;
        this.ikComponent.solveAndStage("", this.getSelectedReplayId());
    }

    /* ====================================================================
     * 区块六：界面
     * ==================================================================== */

    private UIElement buildInterfaceSection()
    {
        UIElement section = new UIElement();
        int y = 8;

        UILabel titleLabel = UI.label(L10n.lang("bbs_ai.panel.interface.title"), 14, 0xAAAAAA);

        titleLabel.relative(section).xy(8, y);
        section.add(titleLabel);
        y += 22;

        this.themeCirculate = new UICirculate((c) -> ThemeManager.get().setTheme(UITheme.byIndex(c.getValue())));

        for (UITheme theme : UITheme.values())
        {
            this.themeCirculate.addLabel(IKey.constant(theme.title));
        }

        this.themeCirculate.setValue(ThemeManager.get().getTheme().ordinal());
        this.themeCirculate.relative(section).xy(8, y).w(1F, -16).h(20);
        section.add(this.themeCirculate);
        y += 26;

        this.languageCirculate = new UICirculate((c) -> LanguageManager.get().setLanguage(mchorse.bbs_ai.ui.language.UILanguage.byIndex(c.getValue())));

        for (mchorse.bbs_ai.ui.language.UILanguage language : mchorse.bbs_ai.ui.language.UILanguage.values())
        {
            this.languageCirculate.addLabel(IKey.constant(language.title));
        }

        this.languageCirculate.setValue(LanguageManager.get().getLanguage().ordinal());
        this.languageCirculate.relative(section).xy(8, y).w(1F, -16).h(20);
        section.add(this.languageCirculate);
        y += 26;

        this.modeCirculate = new UICirculate((c) -> BBSAISettings.uiOperationMode.set(c.getValue()));

        this.modeCirculate.addLabel(IKey.constant("BBS 兼容操作"));
        this.modeCirculate.addLabel(IKey.constant("Blender 风格"));
        this.modeCirculate.addLabel(IKey.constant("Mine-imator 风格"));
        this.modeCirculate.setValue(BBSAISettings.uiOperationMode.get());
        this.modeCirculate.relative(section).xy(8, y).w(1F, -16).h(20);
        section.add(this.modeCirculate);
        y += 30;

        UIButton hotkeys = new UIButton(IKey.constant("热键设置（F1 速查表）"), (b) -> this.openHotkeySettings());

        hotkeys.relative(section).xy(8, y).w(1F, -16).h(22);
        section.add(hotkeys);
        y += 28;

        UIButton guide = new UIButton(IKey.constant("重看新手引导"), (b) ->
        {
            UIOverlay.addOverlay(this.getContext(), new FirstTimeGuide(), 420, 160);
        });

        guide.relative(section).xy(8, y).w(1F, -16).h(22);
        section.add(guide);

        return section;
    }

    /**
     * 打开热键设置
     */
    private void openHotkeySettings()
    {
        UIOverlay.addOverlay(this.getContext(), new HotkeySettingsPanel(false), 460, 420);
    }

    /* ====================================================================
     * 生命周期与事件
     * ==================================================================== */

    @Override
    public void appear()
    {
        super.appear();

        this.refreshImports();

        /* 首次使用引导 */
        if (!BBSAISettings.uiGuideSeen.get())
        {
            UIOverlay.addOverlay(this.getContext(), new FirstTimeGuide(), 420, 160);
        }
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        /* IK Gizmo（固定于面板中心附近） */
        this.gizmo.updateScreens(
            this.area.mx() + 60,
            this.area.my() - 40,
            this.area.mx() + 60,
            this.area.my() - 100
        );

        this.gizmo.render(context);

        /* 预览 HUD 覆盖层 */
        new PreviewRenderer().renderOverlay(context, this.area, 0.0F);
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.gizmo.mousePressed(context))
        {
            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.gizmo.mouseReleased(context))
        {
            return true;
        }

        return super.subMouseReleased(context);
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        /* F1 = 热键速查表 */
        if (context.isPressed(GLFW.GLFW_KEY_F1))
        {
            UIOverlay.addOverlay(this.getContext(), new HotkeySettingsPanel(true), 480, 420);

            return true;
        }

        /* Ctrl+Enter 烘焙 / Ctrl+Esc 放弃（预览模式） */
        PreviewSystem system = PreviewSystem.get();

        if (system.isPreviewMode() && KeyBindingOverrideManager.get().canHandleKeys(context))
        {
            boolean ctrl = context.isHeld(GLFW.GLFW_KEY_LEFT_CONTROL) || context.isHeld(GLFW.GLFW_KEY_RIGHT_CONTROL);

            if (ctrl && context.isPressed(GLFW.GLFW_KEY_ENTER))
            {
                system.bake(mchorse.bbs_ai.preview.BakeMode.OVERWRITE);
                this.notify("烘焙完成");

                return true;
            }

            if (ctrl && context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                system.discard();
                this.notify("已放弃预览");

                return true;
            }
        }

        return super.subKeyPressed(context);
    }

    /**
     * 获取当前角色 ID（v1 默认第一个角色）
     */
    private String getSelectedReplayId()
    {
        return "0";
    }

    /**
     * 面板通知
     */
    private void notify(String message)
    {
        this.getContext().notifyInfo(IKey.constant(message));
    }
}
