package mchorse.bbs_ai.integration;

import mchorse.bbs_ai.BBSAIStudio;
import mchorse.bbs_ai.core.AIServiceManager;
import mchorse.bbs_ai.core.BBSAISettings;
import mchorse.bbs_ai.import_manager.ImportManager;
import mchorse.bbs_ai.preview.PreviewSystem;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/**
 * BBS AI Studio 主初始化入口
 *
 * <p>以独立 Fabric 入口点（fabric.mod.json {@code main} 数组第二项）接入，
 * 在 BBSMod.onInitialize() 完成后执行，等效于「在原版初始化最后追加 AI 初始化」，
 * 且完全不修改原版接口【原版兼容】。</p>
 *
 * <p>初始化顺序（项目规范模块 13）：
 * <ol>
 *   <li>{@link AIServiceManager#initialize()} —— AI 核心（加载加密配置）</li>
 *   <li>注册 bbs_ai 设置（SettingsBuilder）【原版兼容】</li>
 *   <li>{@link ImportManager#initialize()} —— 导入管理</li>
 *   <li>{@link PreviewSystem#initialize()} —— 预览系统</li>
 *   <li>注册 {@code /bbs_ai} 命令</li>
 * </ol>
 * 客户端专属初始化（主题 / 语言 / 热键 / 面板 / 键位）见 BBSAIStudioModClient。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BBSAIStudioMod implements ModInitializer
{
    @Override
    public void onInitialize()
    {
        /* 1. AI 核心服务（加密配置加载 + Provider 构建） */
        AIServiceManager.initialize();

        /* 2. 注册 AI 设置模块（config/bbs/settings/bbs_ai.json）【原版兼容】 */
        BBSMod.setupConfig(Icons.PROCESSOR, "bbs_ai", new java.io.File(BBSMod.getSettingsFolder(), "bbs_ai.json"), BBSAISettings::register);

        /* 配置 → 设置值同步（两处保持一致） */
        BBSAISettings.syncConfigToSettings();

        /* 3. 统一导入管理器（扫描 ai_cache + imports） */
        ImportManager.initialize();

        /* 确保导入目录存在 */
        BBSAIStudio.getAICacheFolder().mkdirs();
        BBSAIStudio.getImportsFolder().mkdirs();

        /* 4. 预烘焙预览系统 */
        PreviewSystem.initialize();

        /* 5. /bbs_ai 命令 */
        CommandRegistrationCallback.EVENT.register(BBSAICommands::register);

        System.out.println("[BBS AI] BBS AI Studio v" + BBSAIStudio.VERSION + " 初始化完成");
    }
}
