package mchorse.bbs_mod.ai;

import java.io.File;
import java.util.function.Supplier;

import mchorse.bbs_ai.BBSAIStudio;
import mchorse.bbs_ai.compat.IModAdapter;
import mchorse.bbs_ai.compat.ModAdapterManager;
import mchorse.bbs_ai.core.AIServiceManager;
import mchorse.bbs_ai.core.BBSAISettings;
import mchorse.bbs_ai.import_manager.ImportManager;
import mchorse.bbs_ai.mods.AIContextService;
import mchorse.bbs_ai.mods.ModCategory;
import mchorse.bbs_ai.mods.ModCompatScanner;
import mchorse.bbs_ai.mods.ModKnowledgeBase;
import mchorse.bbs_ai.preview.PreviewSystem;
import mchorse.bbs_mod.BBSMod;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * AI 底层核心桥（BBS AI Studio 重写进底层的入口）
 *
 * <p>本类位于 {@code mchorse.bbs_mod} 包 —— AI 能力不再以独立 Fabric 入口点
 * 「外挂」在 mod 之后，而是由 {@code BBSMod.onInitialize()} 末尾直接启动，
 * 成为与资源管理、影片管理同级的基础设施。上游任何代码与第三方 addon
 * 都可以通过本门面使用 AI 能力，而无需依赖 {@code mchorse.bbs_ai} 内部实现。</p>
 *
 * <p>职责：
 * <ul>
 *   <li>启动顺序编排：AI 服务 → 设置注册 → 导入/预览 → Mod 兼容扫描</li>
 *   <li>统一生成入口：{@link #generate}（委托 {@link AIServiceManager}）</li>
 *   <li>AI 上下文注册：addon 注入自定义上下文（世界状态、自有 mod 能力说明）</li>
 *   <li>Mod 兼容扩展：追加知识卡片 / 适配器，让 AI 理解更多插件</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class AICore
{
    /**
     * 是否已启动
     */
    private static boolean initialized;

    /**
     * 底层启动（BBSMod.onInitialize 末尾调用，替代原独立 Fabric 入口点）
     */
    public static synchronized void initialize()
    {
        if (initialized)
        {
            return;
        }

        initialized = true;

        /* 1. AI 服务（加密配置加载 + Provider 构建） */
        AIServiceManager.initialize();

        /* 2. 注册 AI 设置模块（config/bbs/settings/bbs_ai.json）【原版兼容】 */
        BBSMod.setupConfig(mchorse.bbs_mod.ui.utils.icons.Icons.PROCESSOR, "bbs_ai", new File(BBSMod.getSettingsFolder(), "bbs_ai.json"), BBSAISettings::register);

        BBSAISettings.syncConfigToSettings();

        /* 3. 统一导入管理器（扫描 ai_cache + imports） */
        ImportManager.initialize();

        BBSAIStudio.getAICacheFolder().mkdirs();
        BBSAIStudio.getImportsFolder().mkdirs();

        /* 4. 预烘焙预览系统 */
        PreviewSystem.initialize();

        /* 5. Mod 兼容层：适配器注册 → 自动识别扫描 → 应用插件调用设置 */
        ModAdapterManager.initialize();
        ModCompatScanner.initialize();
        BBSAISettings.applyPluginSettings();

        /* 6. /bbs_ai 命令 */
        CommandRegistrationCallback.EVENT.register(mchorse.bbs_ai.integration.BBSAICommands::register);

        System.out.println("[BBS AI] AI 底层核心已随 BBSMod 主初始化启动（v" + BBSAIStudio.VERSION + "）");
    }

    /**
     * 是否已启动
     */
    public static boolean isInitialized()
    {
        return initialized;
    }

    /**
     * 统一 AI 生成入口（供上游代码 / 第三方 addon 调用；回调在后台线程）
     */
    public static java.util.concurrent.CompletableFuture<String> generate(String systemPrompt, String userPrompt)
    {
        return AIServiceManager.get().generateAsync(systemPrompt, userPrompt, null, null, null);
    }

    /**
     * 注册额外 AI 上下文片段（每次构建环境摘要时调用；addon 入口）
     */
    public static void registerContextProvider(Supplier<String> context)
    {
        AIContextService.get().registerExtraContext(context);
    }

    /**
     * 为自己的 mod 注册 AI 知识卡片（addon 让 AI 认识自己的 mod）
     */
    public static void registerModKnowledge(String modId, String name, ModCategory category, String summary, String aiHint, String... capabilities)
    {
        ModKnowledgeBase.register(modId, name, category, summary, aiHint, capabilities);

        /* 知识库变化后重扫命中 */
        ModCompatScanner.get().rescan();
        AIContextService.get().invalidate();
    }

    /**
     * 注册 mod AI 兼容适配器（为主流开源 mod 提供深度兼容的 addon 入口）
     */
    public static void registerModAdapter(IModAdapter adapter)
    {
        ModAdapterManager.register(adapter);

        ModCompatScanner.get().rescan();
        AIContextService.get().invalidate();
    }

    /**
     * 当前环境 AI 摘要（mod 自动识别结果 + 注册上下文）
     */
    public static String buildEnvironmentContext()
    {
        return AIContextService.get().buildModDigest(false);
    }
}
