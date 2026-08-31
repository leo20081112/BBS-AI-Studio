package mchorse.bbs_ai.core;

import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.core.ValueString;

/**
 * BBS AI Studio 设置注册
 *
 * <p>所有新增设置项均通过 {@link SettingsBuilder} 注册到 BBS 设置系统【原版兼容】，
 * 生成独立配置文件 {@code config/bbs/settings/bbs_ai.json}。</p>
 *
 * <p>注意：API Key 属于敏感信息，不在本文件明文持久化，仅存储于
 * {@code ai_providers.json}（AES-GCM 加密，见 {@link ConfigSyncManager}），
 * 由 AI 设置面板（客户端）负责输入。</p>
 *
 * <p>设置分类：
 * <ul>
 *   <li>ai —— AI 服务（模式 / 厂商 / 模型 / 采样参数）</li>
 *   <li>motion —— 本地骨骼识别（模型选择 / 采样率 / 平滑）</li>
 *   <li>preview —— 预烘焙预览（洋葱皮 / 冲突标记 / 防抖）</li>
 *   <li>import —— 导入监听</li>
 *   <li>ik —— Blender IK 默认参数</li>
 *   <li>interface —— 界面（主题 / 语言 / 操作模式）</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BBSAISettings
{
    /* ====================================================================
     * AI 服务设置
     * ==================================================================== */

    /**
     * AI 工作模式：0 = 本地模式，1 = API 模式
     */
    public static ValueInt aiMode;

    /**
     * API 厂商索引：0 = OpenAI，1 = Claude，2 = DeepSeek，3 = GLM，4 = 自定义
     */
    public static ValueInt aiProvider;

    /**
     * API Base URL（支持自定义代理）
     */
    public static ValueString aiBaseUrl;

    /**
     * 模型名称
     */
    public static ValueString aiModel;

    /**
     * 采样温度（0.0 ~ 2.0）
     */
    public static ValueFloat aiTemperature;

    /**
     * 最大生成 token 数（256 ~ 8192）
     */
    public static ValueInt aiMaxTokens;

    /* ====================================================================
     * 本地骨骼识别设置
     * ==================================================================== */

    /**
     * 姿态估计模型：0 = YOLOv8-pose，1 = RTMPose
     */
    public static ValueInt motionPoseModel;

    /**
     * 视频采样率：每 N 帧取 1 帧
     */
    public static ValueInt motionSampleRate;

    /**
     * 关键点最低置信度（0.0 ~ 1.0）
     */
    public static ValueFloat motionMinConfidence;

    /**
     * 平滑强度（0.0 = 不平滑，1.0 = 最强）
     */
    public static ValueFloat motionSmoothing;

    /**
     * 是否启用脚部锁定（Foot Lock）
     */
    public static ValueBoolean motionFootLock;

    /* ====================================================================
     * 预览系统设置
     * ==================================================================== */

    /**
     * 洋葱皮帧数范围（前后各 N 帧）
     */
    public static ValueInt previewOnionFrames;

    /**
     * 前帧（未来）洋葱皮轮廓颜色
     */
    public static ValueInt previewOnionNextColor;

    /**
     * 后帧（过去）洋葱皮轮廓颜色
     */
    public static ValueInt previewOnionPrevColor;

    /**
     * 预览 Actor 高亮颜色
     */
    public static ValueInt previewHighlightColor;

    /**
     * 冲突标记颜色
     */
    public static ValueInt previewConflictColor;

    /**
     * 预览参数调整防抖时间（毫秒）
     */
    public static ValueInt previewDebounceMs;

    /* ====================================================================
     * 导入设置
     * ==================================================================== */

    /**
     * 是否启用导入目录自动监听（WatchService）
     */
    public static ValueBoolean importWatchEnabled;

    /* ====================================================================
     * IK 设置
     * ==================================================================== */

    /**
     * 默认 IK 链长度（1 ~ 10）
     */
    public static ValueInt ikDefaultChainLength;

    /**
     * 默认 FK/IK 混合影响（0.0 ~ 1.0）
     */
    public static ValueFloat ikDefaultInfluence;

    /**
     * 默认极向角度（-180 ~ 180）
     */
    public static ValueFloat ikDefaultPoleAngle;

    /**
     * IK 模式：0 = 原生 IK，1 = Blender 风格 IK
     */
    public static ValueInt ikMode;

    /* ====================================================================
     * 界面设置
     * ==================================================================== */

    /**
     * 界面主题：0 = 经典 BBS，1 = Blender 深色，2 = Mine-imator
     */
    public static ValueInt uiTheme;

    /**
     * 界面语言：0 = English，1 = 简体中文，2 = 繁體中文
     */
    public static ValueInt uiLanguage;

    /**
     * 操作模式：0 = BBS 兼容，1 = Blender 风格，2 = Mine-imator 风格
     */
    public static ValueInt uiOperationMode;

    /**
     * 是否已看过首次使用引导
     */
    public static ValueBoolean uiGuideSeen;

    /**
     * 是否正处于「配置 → 设置值」同步过程（避免回调风暴与递归）
     */
    private static boolean syncing;

    /**
     * 注册全部设置【原版兼容】（由 BBSMod.setupConfig 调用）
     */
    public static void register(SettingsBuilder builder)
    {
        registerAiCategory(builder);
        registerMotionCategory(builder);
        registerPreviewCategory(builder);
        registerImportCategory(builder);
        registerIkCategory(builder);
        registerInterfaceCategory(builder);

        /* 设置变更时同步到 AI 服务配置（加密持久化） */
        IValueListener aiSync = (value, flag) ->
        {
            if (!syncing && AIServiceManager.isInitialized())
            {
                syncSettingsToConfig();
            }
        };

        aiMode.postCallback(aiSync);
        aiProvider.postCallback(aiSync);
        aiBaseUrl.postCallback(aiSync);
        aiModel.postCallback(aiSync);
        aiTemperature.postCallback(aiSync);
        aiMaxTokens.postCallback(aiSync);
    }

    /**
     * AI 服务设置分类
     */
    private static void registerAiCategory(SettingsBuilder builder)
    {
        builder.category("ai");

        aiMode = builder.getInt("mode", 1, 0, 1);
        aiProvider = builder.getInt("provider", 0, 0, 4);
        aiBaseUrl = builder.getString("base_url", mchorse.bbs_ai.core.AIConfig.Provider.OPENAI.defaultBaseUrl);
        aiModel = builder.getString("model", mchorse.bbs_ai.core.AIConfig.Provider.OPENAI.defaultModel);
        aiTemperature = builder.getFloat("temperature", 0.7F, 0.0F, 2.0F).slider(0.05D);
        aiMaxTokens = builder.getInt("max_tokens", 4096, 256, 8192).slider();
    }

    /**
     * 本地骨骼识别设置分类
     */
    private static void registerMotionCategory(SettingsBuilder builder)
    {
        builder.category("motion");

        motionPoseModel = builder.getInt("pose_model", 0, 0, 1);
        motionSampleRate = builder.getInt("sample_rate", 2, 1, 10);
        motionMinConfidence = builder.getFloat("min_confidence", 0.3F, 0.0F, 1.0F).slider(0.01D);
        motionSmoothing = builder.getFloat("smoothing", 0.5F, 0.0F, 1.0F).slider(0.01D);
        motionFootLock = builder.getBoolean("foot_lock", true);
    }

    /**
     * 预览系统设置分类
     */
    private static void registerPreviewCategory(SettingsBuilder builder)
    {
        builder.category("preview");

        previewOnionFrames = builder.getInt("onion_frames", 5, 0, 10);
        previewOnionNextColor = builder.getInt("onion_next_color", 0x44FF4444).colorAlpha();
        previewOnionPrevColor = builder.getInt("onion_prev_color", 0x44FF6666).colorAlpha();
        previewHighlightColor = builder.getInt("highlight_color", 0xFF4444FF).colorAlpha();
        previewConflictColor = builder.getInt("conflict_color", 0xFFFFFF00).colorAlpha();
        previewDebounceMs = builder.getInt("debounce_ms", 300, 100, 1000);
    }

    /**
     * 导入设置分类
     */
    private static void registerImportCategory(SettingsBuilder builder)
    {
        builder.category("import");

        importWatchEnabled = builder.getBoolean("watch_enabled", true);
    }

    /**
     * IK 设置分类
     */
    private static void registerIkCategory(SettingsBuilder builder)
    {
        builder.category("ik");

        ikDefaultChainLength = builder.getInt("default_chain_length", 2, 1, 10);
        ikDefaultInfluence = builder.getFloat("default_influence", 1.0F, 0.0F, 1.0F).slider(0.01D);
        ikDefaultPoleAngle = builder.getFloat("default_pole_angle", 0.0F, -180.0F, 180.0F).slider(1.0D);
        ikMode = builder.getInt("ik_mode", 1, 0, 1);
    }

    /**
     * 界面设置分类
     */
    private static void registerInterfaceCategory(SettingsBuilder builder)
    {
        builder.category("interface");

        uiTheme = builder.getInt("theme", 0, 0, 2);
        uiLanguage = builder.getInt("language", 0, 0, 2);
        uiOperationMode = builder.getInt("operation_mode", 0, 0, 2);
        uiGuideSeen = builder.getBoolean("guide_seen", false);
    }

    /**
     * 将设置值同步到 AI 服务配置（触发加密持久化与 Provider 重建）
     */
    public static void syncSettingsToConfig()
    {
        syncing = true;

        try
        {
            AIConfig config = AIServiceManager.get().getConfig();

            config.setMode(aiMode.get() == 0 ? AIConfig.Mode.LOCAL : AIConfig.Mode.API);
            config.setProvider(AIConfig.Provider.values()[Math.min(aiProvider.get(), AIConfig.Provider.values().length - 1)]);
            config.setBaseUrl(aiBaseUrl.get());
            config.setModel(aiModel.get());
            config.setTemperature(aiTemperature.get());
            config.setMaxTokens(aiMaxTokens.get());

            AIServiceManager.get().updateConfig(config);
        }
        finally
        {
            syncing = false;
        }
    }

    /**
     * 将 AI 服务配置回写到设置值（启动时调用，保证两处一致）
     */
    public static void syncConfigToSettings()
    {
        if (!AIServiceManager.isInitialized())
        {
            return;
        }

        syncing = true;

        try
        {
            AIConfig config = AIServiceManager.get().getConfig();

            aiMode.set(config.getMode() == AIConfig.Mode.LOCAL ? 0 : 1);
            aiProvider.set(config.getProvider().ordinal());
            aiBaseUrl.set(config.getBaseUrl());
            aiModel.set(config.getModel());
            aiTemperature.set(config.getTemperature());
            aiMaxTokens.set(config.getMaxTokens());
        }
        finally
        {
            syncing = false;
        }
    }
}
