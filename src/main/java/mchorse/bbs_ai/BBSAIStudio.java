package mchorse.bbs_ai;

import mchorse.bbs_mod.BBSMod;

import java.io.File;

/**
 * BBS AI Studio 常量与目录辅助（主源集）
 *
 * <p>集中定义 AI 模块使用的目录约定：
 * <ul>
 *   <li>{@code config/bbs/ai_cache/} —— 游戏内 AI 生成缓存（动作 JSON、帧缓存、模型文件）</li>
 *   <li>{@code config/bbs/imports/} —— 外部工具链输出目录</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BBSAIStudio
{
    /**
     * AI 模块版本号
     */
    public static final String VERSION = "1.0.0";

    /**
     * 统一导出格式标识
     */
    public static final String MOTION_FORMAT = "bbs_ai_studio_motion_v1";

    /**
     * 获取 AI 缓存目录 {@code config/bbs/ai_cache/}
     */
    public static File getAICacheFolder()
    {
        return BBSMod.getGamePath("config/bbs/ai_cache");
    }

    /**
     * 获取 AI 缓存子路径
     */
    public static File getAICachePath(String path)
    {
        return new File(getAICacheFolder(), path);
    }

    /**
     * 获取外部导入目录 {@code config/bbs/imports/}
     */
    public static File getImportsFolder()
    {
        return BBSMod.getGamePath("config/bbs/imports");
    }

    /**
     * 获取外部导入子路径
     */
    public static File getImportsPath(String path)
    {
        return new File(getImportsFolder(), path);
    }

    /**
     * 获取模型导出目录 {@code config/bbs/models/}（.bbsm 单文件输出）
     */
    public static File getModelExportFolder()
    {
        return BBSMod.getGamePath("config/bbs/models");
    }

    /**
     * 获取模型导出子路径
     */
    public static File getModelExportPath(String path)
    {
        return new File(getModelExportFolder(), path);
    }

    /**
     * 获取外部模型导入目录 {@code config/bbs/imports/models/}（.bbsm / .bbs.json 投放目录）
     */
    public static File getModelImportFolder()
    {
        return BBSMod.getGamePath("config/bbs/imports/models");
    }

    /**
     * 获取外部模型导入子路径
     */
    public static File getModelImportPath(String path)
    {
        return new File(getModelImportFolder(), path);
    }
}
