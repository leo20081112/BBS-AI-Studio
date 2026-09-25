package mchorse.bbs_ai.integration;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import org.lwjgl.glfw.GLFW;

import mchorse.bbs_ai.core.AIConfig;
import mchorse.bbs_ai.core.BBSAISettings;
import mchorse.bbs_ai.core.ConfigSyncManager;
import mchorse.bbs_ai.import_manager.ImportManager;
import mchorse.bbs_ai.preview.PreviewSystem;
import mchorse.bbs_ai.ui.hotkey.HotkeyRegistry;
import mchorse.bbs_ai.ui.language.LanguageManager;
import mchorse.bbs_ai.ui.theme.UITheme;
import mchorse.bbs_ai.ui.panel.UIAIToolsPanel;
import mchorse.bbs_ai.ui.theme.ThemeManager;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.l10n.keys.IKey;

/**
 * BBS AI Studio 客户端底层集成
 *
 * <p>原独立 Fabric 入口点（fabric.mod.json client 数组第二项）重写进底层：
 * 由 {@code BBSModClient.onInitializeClient()} 末尾（BBSClientReadyEvent 之前）
 * 直接调用 {@link #initialize()}，AI 客户端服务与上游客户端服务同级启动。</p>
 *
 * <p>初始化内容：
 * <ul>
 *   <li>注入客户端 UUID 提供者（加密配置密钥派生）</li>
 *   <li>为设置下拉项附加本地化标签</li>
 *   <li>注册 Minecraft KeyBinding（P 预览 / Ctrl+Shift+I 导入 / Ctrl+Shift+M 识别 / Ctrl+Shift+S 分镜）</li>
 *   <li>初始化主题 / 语言 / 热键 / 预览烘焙目标</li>
 *   <li>按设置启动导入目录自动监听</li>
 *   <li>本地调试桥（供 MCP 等外部工具自动化测试）</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BBSAIClientIntegration
{
    /**
     * Minecraft 键绑定：进入/退出预览（P）
     */
    private static KeyBinding keyPreviewToggle;

    /**
     * Minecraft 键绑定：打开导入区块（Ctrl+Shift+I）
     */
    private static KeyBinding keyImportPanel;

    /**
     * Minecraft 键绑定：快速识别（Ctrl+Shift+M）
     */
    private static KeyBinding keyRecognize;

    /**
     * Minecraft 键绑定：分镜生成（Ctrl+Shift+S）
     */
    private static KeyBinding keyStoryboard;

    /**
     * Minecraft 键绑定：导出人物模型（Ctrl+Shift+E）
     */
    private static KeyBinding keyModelExport;

    /**
     * Minecraft 键绑定：导入人物模型 / 模型浏览器（Ctrl+Shift+O / Ctrl+Alt+M）
     */
    private static KeyBinding keyModelImport;
    private static KeyBinding keyModelBrowser;

    /**
     * 是否已初始化
     */
    private static boolean initialized;

    /**
     * 本会话是否已检测过本地组件（每次游戏会话最多询问一次）
     */
    private static boolean componentsPrompted;

    /**
     * 底层启动（BBSModClient.onInitializeClient 末尾调用）
     */
    public static synchronized void initialize()
    {
        if (initialized)
        {
            return;
        }

        initialized = true;

        /* 1. 客户端 UUID 提供者（加密配置共享契约） */
        ConfigSyncManager.setUuidSupplier(() ->
        {
            try
            {
                java.util.UUID uuid = MinecraftClient.getInstance().getSession().getUuidOrNull();

                return uuid == null ? null : uuid.toString();
            }
            catch (Exception e)
            {
                return null;
            }
        });

        /* 2. 设置下拉项本地化标签【原版兼容】 */
        BBSAISettings.aiMode.modes(IKey.constant("本地模式（ONNX 骨骼识别）"), IKey.constant("API 模式（云端大模型）"));
        BBSAISettings.aiProvider.modes(
            IKey.constant(AIConfig.Provider.OPENAI.title),
            IKey.constant(AIConfig.Provider.CLAUDE.title),
            IKey.constant(AIConfig.Provider.DEEPSEEK.title),
            IKey.constant(AIConfig.Provider.GLM.title),
            IKey.constant(AIConfig.Provider.CUSTOM.title)
        );
        BBSAISettings.uiTheme.modes(
            IKey.constant(UITheme.CLASSIC_BBS.title),
            IKey.constant(UITheme.BLENDER_DARK.title),
            IKey.constant(UITheme.MINEIMATOR.title)
        );
        BBSAISettings.uiLanguage.modes(
            IKey.constant("English"),
            IKey.constant("简体中文"),
            IKey.constant("繁體中文")
        );
        BBSAISettings.uiOperationMode.modes(
            IKey.constant("BBS 兼容操作"),
            IKey.constant("Blender 风格"),
            IKey.constant("Mine-imator 风格")
        );
        BBSAISettings.motionPoseModel.modes(IKey.constant("YOLOv8-pose（17 点）"), IKey.constant("RTMPose（17 点，更精细）"));
        BBSAISettings.ikMode.modes(IKey.constant("原生 IK"), IKey.constant("Blender 风格 IK"));

        /* 3. 初始化客户端服务 */
        LanguageManager.initialize();
        ThemeManager.initialize();
        HotkeyRegistry.initialize();

        /* 预览系统烘焙目标：写入 FilmManager 离线数据（仪表盘实时编辑由面板内完成） */
        PreviewSystem.get().setBakeTarget(new PreviewSystem.ManagerBakeTarget());

        /* 4. Minecraft 键绑定 */
        keyPreviewToggle = createKey("ai_preview", GLFW.GLFW_KEY_P);
        keyImportPanel = createKey("ai_import", GLFW.GLFW_KEY_I);
        keyRecognize = createKey("ai_recognize", GLFW.GLFW_KEY_M);
        keyStoryboard = createKey("ai_storyboard", GLFW.GLFW_KEY_S);
        keyModelExport = createKey("model_export", GLFW.GLFW_KEY_E);
        keyModelImport = createKey("model_import", GLFW.GLFW_KEY_O);
        keyModelBrowser = createKey("model_browser", GLFW.GLFW_KEY_M);

        /* 5. 每帧轮询键位 */
        ClientTickEvents.END_CLIENT_TICK.register((client) ->
        {
            while (keyPreviewToggle.wasPressed())
            {
                togglePreview();
            }

            while (keyImportPanel.wasPressed())
            {
                if (hasCtrl(client))
                {
                    showSection("import");
                }
            }

            while (keyRecognize.wasPressed())
            {
                if (hasCtrl(client))
                {
                    showSection("video");
                }
            }

            while (keyStoryboard.wasPressed())
            {
                if (hasCtrl(client))
                {
                    openAIEditor();
                }
            }

            while (keyModelExport.wasPressed())
            {
                if (hasCtrl(client))
                {
                    openModelExport();
                }
            }

            while (keyModelImport.wasPressed())
            {
                if (hasCtrl(client))
                {
                    openModelBrowser();
                }
            }

            while (keyModelBrowser.wasPressed())
            {
                if (hasCtrl(client) && hasAlt(client))
                {
                    openModelBrowser();
                }
            }
        });

        /* 6. 导入目录自动监听（按设置） */
        if (BBSAISettings.importWatchEnabled.get())
        {
            ImportManager.get().startWatching();
        }

        /* 7. 进入世界后检测本地组件（FFmpeg / ONNX 模型），缺失时弹出安装询问 */
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
        {
            checkLocalComponents(client);
        });

        /* 8. 本地调试桥（127.0.0.1 截图/命令/日志，供 MCP 等外部工具自动化测试；-Dbbsai.bridge.port=0 关闭） */
        mchorse.bbs_ai.debug.BBSAIDebugBridge.get().start();

        System.out.println("[BBS AI] AI 客户端集成已随 BBSModClient 主初始化启动");
    }

    /**
     * 进入世界后后台检测缺失的本地组件；有缺失且用户未禁用提示时，
     * 回到主线程打开 BBS 界面并弹出组件安装询问面板
     */
    private static void checkLocalComponents(net.minecraft.client.MinecraftClient client)
    {
        if (componentsPrompted)
        {
            return;
        }

        componentsPrompted = true;

        if (BBSAISettings.componentsPromptDisabled != null && BBSAISettings.componentsPromptDisabled.get())
        {
            return;
        }

        Thread thread = new Thread(() ->
        {
            java.util.List<mchorse.bbs_ai.core.LocalComponentsDownloader.Component> missing =
                mchorse.bbs_ai.core.LocalComponentsDownloader.findMissing();

            if (missing.isEmpty())
            {
                return;
            }

            client.execute(() ->
            {
                try
                {
                    mchorse.bbs_mod.ui.dashboard.UIDashboard dashboard = mchorse.bbs_mod.BBSModClient.getDashboard();

                    mchorse.bbs_mod.ui.framework.UIScreen.open(dashboard);
                    mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay.addOverlay(
                        dashboard.context,
                        new mchorse.bbs_ai.ui.panel.UIComponentsSetupPanel(missing),
                        380,
                        260
                    );
                }
                catch (Exception e)
                {
                    System.err.println("[BBS AI] 弹出本地组件安装面板失败：" + e.getMessage());
                }
            });
        }, "BBS AI 组件检测");

        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 注册键绑定
     */
    private static KeyBinding createKey(String id, int key)
    {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key." + BBSMod.MOD_ID + "." + id,
            InputUtil.Type.KEYSYM,
            key,
            "category." + BBSMod.MOD_ID + ".main"
        ));
    }

    /**
     * 切换预览模式（P 键）
     */
    private static void togglePreview()
    {
        PreviewSystem system = PreviewSystem.get();

        if (system.isPreviewMode())
        {
            system.discard();
        }
        else
        {
            if (system.getCache().size() == 0)
            {
                System.out.println("[BBS AI] 没有暂存的预览数据（先用 AI 生成或导入动作）");
            }
        }
    }

    /**
     * 打开 AI 编辑器面板（动画生成统一入口，Ctrl+Shift+S）
     */
    public static void openAIEditor()
    {
        try
        {
            mchorse.bbs_mod.ui.dashboard.UIDashboard dashboard = mchorse.bbs_mod.BBSModClient.getDashboard();
            mchorse.bbs_ai.ui.editor.UIAIEditorPanel panel = dashboard.getPanel(mchorse.bbs_ai.ui.editor.UIAIEditorPanel.class);

            if (panel == null)
            {
                return;
            }

            dashboard.setPanel(panel);
            mchorse.bbs_mod.ui.framework.UIScreen.open(dashboard);
        }
        catch (Exception e)
        {
            System.err.println("[BBS AI] 打开 AI 编辑器失败：" + e.getMessage());
        }
    }

    /**
     * 打开 AI 工具面板并展开指定区块
     */
    public static void showSection(String id)
    {
        try
        {
            mchorse.bbs_mod.ui.dashboard.UIDashboard dashboard = mchorse.bbs_mod.BBSModClient.getDashboard();
            UIAIToolsPanel panel = dashboard.getPanel(UIAIToolsPanel.class);

            if (panel == null)
            {
                return;
            }

            panel.showSection(id);

            /* bbs-fs 2.6 面板即标签页：先切换再打开屏幕，否则停留在原面板 */
            dashboard.setPanel(panel);
            mchorse.bbs_mod.ui.framework.UIScreen.open(dashboard);
        }
        catch (Exception e)
        {
            System.err.println("[BBS AI] 打开 AI 工具面板失败：" + e.getMessage());
        }
    }

    /**
     * 是否按着 Ctrl
     */
    private static boolean hasCtrl(MinecraftClient client)
    {
        long handle = client.getWindow().getHandle();

        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    /**
     * 是否按着 Alt
     */
    private static boolean hasAlt(MinecraftClient client)
    {
        long handle = client.getWindow().getHandle();

        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    /**
     * 打开模型导出对话框（Ctrl+Shift+E）
     */
    private static void openModelExport()
    {
        try
        {
            mchorse.bbs_mod.ui.dashboard.UIDashboard dashboard = mchorse.bbs_mod.BBSModClient.getDashboard();

            mchorse.bbs_mod.ui.framework.UIScreen.open(dashboard);
            mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay.addOverlay(dashboard.context, new mchorse.bbs_ai.ui.model.ExportModelPanel(), 340, 300);
        }
        catch (Exception e)
        {
            System.err.println("[BBS AI] 打开模型导出面板失败：" + e.getMessage());
        }
    }

    /**
     * 打开模型浏览器（Ctrl+Shift+O / Ctrl+Alt+M）
     */
    private static void openModelBrowser()
    {
        try
        {
            mchorse.bbs_mod.ui.dashboard.UIDashboard dashboard = mchorse.bbs_mod.BBSModClient.getDashboard();

            mchorse.bbs_mod.ui.framework.UIScreen.open(dashboard);
            mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay.addOverlay(dashboard.context, new mchorse.bbs_ai.ui.model.ModelBrowserPanel(), 380, 340);
        }
        catch (Exception e)
        {
            System.err.println("[BBS AI] 打开模型浏览器失败：" + e.getMessage());
        }
    }
}
