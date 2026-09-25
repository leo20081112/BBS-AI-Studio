package mchorse.bbs_ai.ui.panel;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.glfw.GLFW;

import mchorse.bbs_ai.core.AIConfig;
import mchorse.bbs_ai.core.AIServiceManager;
import mchorse.bbs_ai.core.BBSAISettings;
import mchorse.bbs_ai.core.api.APIException;
import mchorse.bbs_ai.ik.BlenderIKComponent;
import mchorse.bbs_ai.ik.BlenderIKSettingsPanel;
import mchorse.bbs_ai.ik.IKBone;
import mchorse.bbs_ai.ik.IKBoneChain;
import mchorse.bbs_ai.import_manager.ImportEntry;
import mchorse.bbs_ai.import_manager.ImportManager;
import mchorse.bbs_ai.mods.AIContextService;
import mchorse.bbs_ai.mods.ModCompatScanner;
import mchorse.bbs_ai.mods.ModInfo;
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
import mchorse.bbs_ai.ui.language.UILanguage;
import mchorse.bbs_ai.ui.theme.ThemeManager;
import mchorse.bbs_ai.ui.theme.UITheme;
import mchorse.bbs_ai.ui.transform.KeyBindingOverrideManager;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * BBS AI Studio 主面板（AI 工具）
 *
 * <p>原生风格重构：全部功能以 {@code UISection} 折叠卡片纵向堆叠在滚动区里
 * （与上游 Model blocks / Particles 面板同款观感），替代旧版顶部图标切换。
 * 功能区块：AI 设置 / 分镜生成 / 视频识别 / 导入管理 / 人物模型 / IK 调整 /
 * Mod 兼容（自动识别环境）/ 界面；底部为快捷键状态栏。</p>
 *
 * <p>数据流遵循项目规范：所有生成结果先进预览系统，
 * 经 BakeConfirmationDialog 确认后才写入正式 Film【原版兼容】。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class UIAIToolsPanel extends UIDashboardPanel
{
    /**
     * 区块折叠状态记忆（面板重建后保持）
     */
    private final Map<String, Boolean> folds = new HashMap<>();

    /**
     * 区块 id → UISection（showSection 展开）
     */
    private final Map<String, mchorse.bbs_mod.ui.framework.elements.UISection> sections = new HashMap<>();

    /* ---- 导入管理 ---- */
    private UIStringList importList;
    private List<ImportEntry> entries = new ArrayList<>();
    private UILabel importInfo;

    /* ---- 视频识别 ---- */
    private UITextbox videoPath;
    private UIToggle mirrorToggle;
    private UILabel recognizeProgress;
    private final VideoRecognitionPipeline pipeline = new VideoRecognitionPipeline();

    /* ---- 分镜生成（已迁移至 AI 编辑器，此区块为入口） ---- */
    private UILabel storyboardHint;

    /* ---- AI 设置 ---- */
    private UICirculate providerCirculate;
    private UITextbox apiKeyBox;
    private boolean showApiKey;
    private UITextbox baseUrlBox;
    private UITextbox modelBox;
    private UILabel testResult;

    /* ---- Mod 兼容 ---- */
    private UILabel modsSummary;
    private UIStringList modsList;
    private UILabel modsInfo;
    private List<ModInfo> modInfos = new ArrayList<>();

    /* ---- IK ---- */
    private final BlenderIKComponent ikComponent;

    /* ---- 界面 ---- */
    private UICirculate themeCirculate;
    private UICirculate languageCirculate;
    private UICirculate modeCirculate;

    /* ---- 状态栏 ---- */
    private final StatusBar statusBar = new StatusBar();

    /**
     * 预览 HUD 渲染器（复用实例，复杂场景下减少每帧分配）
     */
    private final PreviewRenderer previewRenderer = new PreviewRenderer();

    public UIAIToolsPanel(UIDashboard dashboard)
    {
        super(dashboard);

        /* IK 组件（head 链演示，供约束面板与解算预览使用） */
        this.ikComponent = new BlenderIKComponent("ik_head", this.buildDemoChain());

        /* 主滚动区：折叠区块纵向堆叠（上游原生形态） */
        UIScrollView scroll = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING,
            this.buildSettingsSection(),
            this.buildStoryboardSection(),
            this.buildVideoSection(),
            this.buildImportSection(),
            this.buildModelSection(),
            this.buildIKSection(),
            this.buildModsSection(),
            this.buildInterfaceSection()
        );

        scroll.relative(this).xy(0, 0).w(1F).h(1F, -18);
        this.statusBar.relative(this).y(1F, -16).w(1F).h(16);

        this.add(scroll, this.statusBar);

        /* bbs-fs 2.6 起 appear/disappear 为 final，生命周期逻辑改在构造期注册回调 */
        this.onAppear(() ->
        {
            this.refreshImports();
            this.refreshMods();

            /* 首次使用引导（不传尺寸，面板按内容自适应高度） */
            if (!BBSAISettings.uiGuideSeen.get())
            {
                UIOverlay.addOverlay(this.getContext(), new FirstTimeGuide());
            }
        });
    }

    /**
     * 构建折叠区块（统一标题/折叠记忆/默认展开状态）
     */
    private mchorse.bbs_mod.ui.framework.elements.UISection section(String id, String titleKey, boolean expanded)
    {
        mchorse.bbs_mod.ui.framework.elements.UISection section = new mchorse.bbs_mod.ui.framework.elements.UISection(L10n.lang(titleKey));

        section.remember(this.folds, id, expanded);
        this.sections.put(id, section);

        return section;
    }

    /**
     * 展开并定位指定区块（键位快捷入口：import/video/storyboard）
     */
    public void showSection(String id)
    {
        mchorse.bbs_mod.ui.framework.elements.UISection section = this.sections.get(id);

        if (section != null)
        {
            section.setExpanded(true);
        }
    }

    /* ====================================================================
     * 区块：AI 设置
     * ==================================================================== */

    private mchorse.bbs_mod.ui.framework.elements.UISection buildSettingsSection()
    {
        mchorse.bbs_mod.ui.framework.elements.UISection section = this.section("settings", "bbs_ai.panel.tab.settings", true);
        AIConfig config = AIServiceManager.get().getConfig();

        this.providerCirculate = new UICirculate((c) -> this.applyProvider(c.getValue()));

        for (AIConfig.Provider provider : AIConfig.Provider.values())
        {
            this.providerCirculate.addLabel(IKey.constant(provider.title));
        }

        this.providerCirculate.setValue(config.getProvider().ordinal());

        /* API Key（掩码 + 显示切换） */
        this.apiKeyBox = new UITextbox(4096, (t) -> this.pushConfig());
        this.apiKeyBox.setText(this.showApiKey ? config.getApiKey() : this.maskKey(config.getApiKey()));

        UIIcon show = new UIIcon(this.showApiKey ? Icons.INVISIBLE : Icons.VISIBLE, (b) -> this.toggleShowKey());
        show.tooltip(L10n.lang("bbs_ai.str.toolsPanel.1"));

        this.baseUrlBox = new UITextbox(2048, (t) -> this.pushConfig());
        this.baseUrlBox.setText(config.getBaseUrl());

        this.modelBox = new UITextbox(512, (t) -> this.pushConfig());
        this.modelBox.setText(config.getModel());

        UIButton test = new UIButton(L10n.lang("bbs_ai.panel.settings.test"), (b) -> this.testConnection());

        this.testResult = UI.label(IKey.constant(" "), 14, Colors.GRAY);

        section.fields.add(
            this.providerCirculate,
            UI.labelRow(IKey.constant("API Key"), this.apiKeyBox),
            show,
            UI.labelRow(IKey.constant("Base URL"), this.baseUrlBox),
            UI.labelRow(L10n.lang("bbs_ai.str.toolsPanel.2"), this.modelBox),
            test,
            this.testResult
        );

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
        this.testResult.label = L10n.lang("bbs_ai.str.toolsPanel.3");
        this.testResult.color(Colors.GRAY);

        AIServiceManager.get().testConnectionAsync(
            (ok) -> this.testResult.label = L10n.lang("bbs_ai.str.toolsPanel.4"),
            (APIException error) -> this.testResult.label = IKey.constant("X " + error.getUserMessage())
        );
    }

    /* ====================================================================
     * 区块：分镜生成
     * ==================================================================== */

    private mchorse.bbs_mod.ui.framework.elements.UISection buildStoryboardSection()
    {
        mchorse.bbs_mod.ui.framework.elements.UISection section = this.section("storyboard", "bbs_ai.panel.tab.storyboard", false);

        /* 动画生成类功能已统一迁移到 AI 编辑器（专门的生成界面） */
        this.storyboardHint = UI.label(L10n.lang("bbs_ai.panel.storyboard.moved"), 14, Colors.GRAY);

        UIButton openEditor = new UIButton(L10n.lang("bbs_ai.panel.storyboard.open_editor"), (b) -> this.openAIEditor());

        section.fields.add(
            this.storyboardHint,
            openEditor
        );

        return section;
    }

    /**
     * 切换到 AI 编辑器面板（动画生成统一入口）
     */
    private void openAIEditor()
    {
        mchorse.bbs_ai.ui.editor.UIAIEditorPanel editor = this.dashboard.getPanel(mchorse.bbs_ai.ui.editor.UIAIEditorPanel.class);

        if (editor != null)
        {
            this.dashboard.setPanel(editor);
        }
    }

    /* ====================================================================
     * 区块：视频识别
     * ==================================================================== */

    private mchorse.bbs_mod.ui.framework.elements.UISection buildVideoSection()
    {
        mchorse.bbs_mod.ui.framework.elements.UISection section = this.section("video", "bbs_ai.panel.tab.video", false);

        this.videoPath = new UITextbox(4096, (t) -> {});
        this.videoPath.placeholder(L10n.lang("bbs_ai.panel.video.path"));

        this.mirrorToggle = new UIToggle(L10n.lang("bbs_ai.panel.video.mirror"), false, (b) -> {});

        UIButton start = new UIButton(L10n.lang("bbs_ai.panel.video.start"), (b) -> this.startRecognition());

        /* 本地组件手动检查入口（错过进世界弹窗的用户从这里补装） */
        UIButton components = new UIButton(L10n.lang("bbs_ai.panel.video.components"), (b) -> this.openComponentsPanel());

        this.recognizeProgress = UI.label(L10n.lang("bbs_ai.panel.video.hint"), 14, Colors.GRAY);

        section.fields.add(
            UI.labelRow(L10n.lang("bbs_ai.str.toolsPanel.5"), this.videoPath),
            this.mirrorToggle,
            start,
            components,
            this.recognizeProgress
        );

        return section;
    }

    /**
     * 手动检查本地组件：后台探测缺失后在当前界面弹出安装面板
     */
    private void openComponentsPanel()
    {
        Thread thread = new Thread(() ->
        {
            java.util.List<mchorse.bbs_ai.core.LocalComponentsDownloader.Component> missing =
                mchorse.bbs_ai.core.LocalComponentsDownloader.findMissing();

            net.minecraft.client.MinecraftClient.getInstance().execute(() ->
            {
                if (missing.isEmpty())
                {
                    this.getContext().notifyInfo(L10n.lang("bbs_ai.panel.components.all_ready"));

                    return;
                }

                UIOverlay.addOverlay(
                    this.getContext(),
                    new mchorse.bbs_ai.ui.panel.UIComponentsSetupPanel(missing),
                    380,
                    260
                );
            });
        }, "BBS AI 组件检测");

        thread.setDaemon(true);
        thread.start();
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

        this.recognizeProgress.label = L10n.lang("bbs_ai.str.toolsPanel.6");

        this.pipeline.start(video,
            BBSAISettings.motionSampleRate.get(),
            this.mirrorToggle.getValue(),
            "",
            this.getSelectedReplayId(),
            (p) -> this.recognizeProgress.label = IKey.constant(String.format("识别中... %d%%", (int) (p * 100))),
            (file) -> this.recognizeProgress.label = IKey.constant("完成！输出：" + file.getName()),
            (error) -> this.recognizeProgress.label = IKey.constant("失败：" + error)
        );
    }

    /* ====================================================================
     * 区块：导入管理
     * ==================================================================== */

    private mchorse.bbs_mod.ui.framework.elements.UISection buildImportSection()
    {
        mchorse.bbs_mod.ui.framework.elements.UISection section = this.section("import", "bbs_ai.panel.tab.import", false);

        this.importList = new UIStringList((l) -> this.updateImportInfo());
        this.importList.background();
        this.importList.h(100);

        UIButton refresh = new UIButton(L10n.lang("bbs_ai.panel.import.refresh"), (b) ->
        {
            ImportManager.get().scanAllSources();
            this.refreshImports();
        });

        UIButton preview = new UIButton(L10n.lang("bbs_ai.panel.import.preview"), (b) -> this.previewSelectedImport());
        UIButton bake = new UIButton(L10n.lang("bbs_ai.panel.import.bake"), (b) -> this.bakeSelectedImport());

        this.importInfo = UI.label(L10n.lang("bbs_ai.panel.import.empty"), 14, Colors.GRAY);

        section.fields.add(
            this.importList,
            UI.row(4, refresh, preview, bake),
            this.importInfo
        );

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
     * 区块：人物模型
     * ==================================================================== */

    private mchorse.bbs_mod.ui.framework.elements.UISection buildModelSection()
    {
        mchorse.bbs_mod.ui.framework.elements.UISection section = this.section("model", "bbs_ai.panel.tab.model", false);

        /* 人物模型导出/浏览器/映射已并入「人物模型编辑页」动作栏 AI 按钮，此处为快捷跳转 */
        UIButton openEditor = new UIButton(L10n.lang("bbs_ai.panel.model.open_editor"), (b) ->
        {
            mchorse.bbs_mod.ui.model_editor.UIModelEditorPanel panel = this.dashboard.getPanel(mchorse.bbs_mod.ui.model_editor.UIModelEditorPanel.class);

            if (panel != null)
            {
                this.dashboard.setPanel(panel);
            }
        });

        section.fields.add(
            UI.label(L10n.lang("bbs_ai.panel.model.moved"), 14, Colors.GRAY),
            openEditor
        );

        return section;
    }

    /* ====================================================================
     * 区块：IK 调整
     * ==================================================================== */

    private mchorse.bbs_mod.ui.framework.elements.UISection buildIKSection()
    {
        mchorse.bbs_mod.ui.framework.elements.UISection section = this.section("ik", "bbs_ai.panel.tab.ik", false);

        UIButton openPanel = new UIButton(L10n.lang("bbs_ai.str.toolsPanel.7"), (b) ->
        {
            UIOverlay.addOverlay(this.getContext(), new BlenderIKSettingsPanel(this.ikComponent, this::solveIK), 300, 440);
        });

        UIButton solve = new UIButton(L10n.lang("bbs_ai.str.toolsPanel.8"), (b) -> this.solveIK());
        UIButton bake = new UIButton(L10n.lang("bbs_ai.str.toolsPanel.9"), (b) -> this.bakeSelectedImport());

        section.fields.add(
            UI.label(L10n.lang("bbs_ai.str.toolsPanel.10"), 14, Colors.GRAY),
            openPanel,
            solve,
            bake
        );

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
     * 区块：Mod 兼容（自动识别环境）
     * ==================================================================== */

    private mchorse.bbs_mod.ui.framework.elements.UISection buildModsSection()
    {
        mchorse.bbs_mod.ui.framework.elements.UISection section = this.section("mods", "bbs_ai.panel.tab.mods", false);

        this.modsSummary = UI.label(IKey.constant(" "), 14, Colors.GRAY);

        this.modsList = new UIStringList((l) -> this.updateModsInfo());
        this.modsList.background();
        this.modsList.h(100);

        this.modsInfo = UI.label(L10n.lang("bbs_ai.panel.mods.empty"), 14, Colors.GRAY);

        UIButton rescan = new UIButton(L10n.lang("bbs_ai.panel.mods.rescan"), (b) -> this.rescanMods());
        UIButton exportReport = new UIButton(L10n.lang("bbs_ai.panel.mods.export"), (b) -> this.exportModsReport());

        section.fields.add(
            this.modsSummary,
            this.modsList,
            UI.row(4, rescan, exportReport),
            this.modsInfo
        );

        return section;
    }

    /**
     * 刷新 mod 列表（懒扫描：首次调用会补齐注册表统计）
     */
    private void refreshMods()
    {
        if (this.modsList == null)
        {
            return;
        }

        try
        {
            this.modInfos = new ArrayList<>(ModCompatScanner.get().getMods());
            this.modsSummary.label = IKey.constant(ModCompatScanner.get().summaryLine());

            this.modsList.clear();

            for (ModInfo info : this.modInfos)
            {
                this.modsList.add(info.toListLabel());
            }

            this.modsList.setIndex(-1);
        }
        catch (Exception e)
        {
            this.modsSummary.label = IKey.constant("Mod 扫描失败：" + e.getMessage());
        }
    }

    /**
     * 重新扫描（清除缓存后全量重建）
     */
    private void rescanMods()
    {
        ModCompatScanner.get().rescan();
        AIContextService.get().invalidate();
        this.refreshMods();
        this.notify("Mod 环境已重新扫描");
    }

    /**
     * 导出 mods_report.json（外部工具链 / AI 共享）
     */
    private void exportModsReport()
    {
        File report = ModCompatScanner.get().saveReport();

        this.notify(report != null
            ? "已导出：" + report.getAbsolutePath()
            : "导出失败（详见日志）");
    }

    /**
     * 更新选中 mod 的详细信息（知识卡片 + 统计 + 适配器说明）
     */
    private void updateModsInfo()
    {
        if (this.modsInfo == null)
        {
            return;
        }

        int index = this.modsList == null ? -1 : this.modsList.getIndex();
        ModInfo info = index >= 0 && index < this.modInfos.size() ? this.modInfos.get(index) : null;

        if (info == null)
        {
            this.modsInfo.label = L10n.lang("bbs_ai.panel.mods.empty");

            return;
        }

        StringBuilder builder = new StringBuilder();

        if (info.knowledge != null)
        {
            builder.append(info.knowledge.summary).append(" 拍摄建议：").append(info.knowledge.aiHint);
        }
        else
        {
            builder.append("未收录 mod（").append(info.category.title).append("）");

            if (!info.authors.isEmpty())
            {
                builder.append(" 作者：").append(info.authors);
            }
        }

        if (info.items >= 0 || info.blocks >= 0 || info.entities >= 0)
        {
            builder.append(String.format(" · 物品 %d / 方块 %d / 实体 %d", Math.max(0, info.items), Math.max(0, info.blocks), Math.max(0, info.entities)));
        }

        for (String note : info.adapterNotes)
        {
            builder.append("\n· ").append(note);
        }

        this.modsInfo.label = IKey.constant(builder.toString());
    }

    /* ====================================================================
     * 区块：界面
     * ==================================================================== */

    private mchorse.bbs_mod.ui.framework.elements.UISection buildInterfaceSection()
    {
        mchorse.bbs_mod.ui.framework.elements.UISection section = this.section("interface", "bbs_ai.panel.tab.interface", false);

        this.themeCirculate = new UICirculate((c) -> ThemeManager.get().setTheme(UITheme.byIndex(c.getValue())));

        for (UITheme theme : UITheme.values())
        {
            this.themeCirculate.addLabel(IKey.constant(theme.title));
        }

        this.themeCirculate.setValue(ThemeManager.get().getTheme().ordinal());

        this.languageCirculate = new UICirculate((c) -> LanguageManager.get().setLanguage(UILanguage.byIndex(c.getValue())));

        for (UILanguage language : UILanguage.values())
        {
            this.languageCirculate.addLabel(IKey.constant(language.title));
        }

        this.languageCirculate.setValue(LanguageManager.get().getLanguage().ordinal());

        this.modeCirculate = new UICirculate((c) -> BBSAISettings.uiOperationMode.set(c.getValue()));

        this.modeCirculate.addLabel(L10n.lang("bbs_ai.str.toolsPanel.11"));
        this.modeCirculate.addLabel(L10n.lang("bbs_ai.str.toolsPanel.12"));
        this.modeCirculate.addLabel(L10n.lang("bbs_ai.str.toolsPanel.13"));
        this.modeCirculate.setValue(BBSAISettings.uiOperationMode.get());

        UIButton hotkeys = new UIButton(L10n.lang("bbs_ai.str.toolsPanel.14"), (b) -> this.openHotkeySettings());

        UIButton guide = new UIButton(L10n.lang("bbs_ai.str.toolsPanel.15"), (b) ->
        {
            UIOverlay.addOverlay(this.getContext(), new FirstTimeGuide());
        });

        section.fields.add(
            UI.labelRow(L10n.lang("bbs_ai.str.toolsPanel.16"), this.themeCirculate),
            UI.labelRow(L10n.lang("bbs_ai.str.toolsPanel.17"), this.languageCirculate),
            UI.labelRow(L10n.lang("bbs_ai.str.toolsPanel.18"), this.modeCirculate),
            hotkeys,
            guide
        );

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
    public void render(UIContext context)
    {
        super.render(context);

        /* 预览 HUD 覆盖层（复用实例，复杂场景下减少每帧分配） */
        this.previewRenderer.renderOverlay(context, this.area, 0.0F);
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
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
