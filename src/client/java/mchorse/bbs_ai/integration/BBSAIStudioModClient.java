package mchorse.bbs_ai.integration;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import org.lwjgl.glfw.GLFW;

import mchorse.bbs_ai.core.AIConfig;
import mchorse.bbs_ai.core.AIServiceManager;
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
 * BBS AI Studio 客户端初始化入口
 *
 * <p>在 BBSModClient 之后执行（fabric.mod.json {@code client} 数组第二项）：
 * <ul>
 *   <li>注入客户端 UUID 提供者（加密配置密钥派生）</li>
 *   <li>为设置下拉项附加本地化标签（modes）</li>
 *   <li>注册 Minecraft KeyBinding（P 预览 / Ctrl+Shift+I 导入 / Ctrl+Shift+M 识别 / Ctrl+Shift+S 分镜）</li>
 *   <li>初始化主题 / 语言 / 热键 / 预览烘焙目标</li>
 *   <li>按设置启动导入目录自动监听</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BBSAIStudioModClient implements ClientModInitializer
{
    /**
     * Minecraft 键绑定：进入/退出预览（P）
     */
    private static KeyBinding keyPreviewToggle;

    /**
     * Minecraft 键绑定：打开导入面板（Ctrl+Shift+I）
     */
    private static KeyBinding keyImportPanel;

    /**
     * Minecraft 键绑定：快速识别（Ctrl+Shift+M，打开视频识别区块）
     */
    private static KeyBinding keyRecognize;

    /**
     * Minecraft 键绑定：分镜生成（Ctrl+Shift+S）
     */
    private static KeyBinding keyStoryboard;

    @Override
    public void onInitializeClient()
    {
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
        keyPreviewToggle = this.createKey("ai_preview", GLFW.GLFW_KEY_P);
        keyImportPanel = this.createKey("ai_import", GLFW.GLFW_KEY_I);
        keyRecognize = this.createKey("ai_recognize", GLFW.GLFW_KEY_M);
        keyStoryboard = this.createKey("ai_storyboard", GLFW.GLFW_KEY_S);

        /* 5. 每帧轮询键位 */
        ClientTickEvents.END_CLIENT_TICK.register((client) ->
        {
            while (keyPreviewToggle.wasPressed())
            {
                this.togglePreview();
            }

            while (keyImportPanel.wasPressed())
            {
                if (this.hasCtrl(client))
                {
                    this.showSection("import");
                }
            }

            while (keyRecognize.wasPressed())
            {
                if (this.hasCtrl(client))
                {
                    this.showSection("video");
                }
            }

            while (keyStoryboard.wasPressed())
            {
                if (this.hasCtrl(client))
                {
                    this.showSection("storyboard");
                }
            }
        });

        /* 6. 导入目录自动监听（按设置） */
        if (BBSAISettings.importWatchEnabled.get())
        {
            ImportManager.get().startWatching();
        }

        System.out.println("[BBS AI] 客户端初始化完成");
    }

    /**
     * 注册键绑定
     */
    private KeyBinding createKey(String id, int key)
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
    private void togglePreview()
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
     * 打开 AI 工具面板并切换区块
     */
    private void showSection(String id)
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
    private boolean hasCtrl(MinecraftClient client)
    {
        long handle = client.getWindow().getHandle();

        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
            || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }
}
