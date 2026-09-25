package mchorse.bbs_ai.compat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mchorse.bbs_ai.mods.ModCompatScanner;
import mchorse.bbs_ai.mods.ModInfo;

/**
 * Mod 适配器注册中心
 *
 * <p>内置一批主流开源 mod 的适配器，并提供 {@link #register} 供第三方追加。
 * {@link ModCompatScanner} 扫描完成后调用 {@link #applyAll}，
 * 把各适配器的说明合并进对应 {@link ModInfo}（进入 AI 上下文）。</p>
 *
 * <p>适配器可被用户在 AI 设置（插件调用）中单独停用：停用列表持久化在
 * {@code bbs_ai.config.plugins.adapters_disabled}（逗号分隔 id），
 * 由 {@link #applyDisabledList} 应用。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ModAdapterManager
{
    /**
     * 已注册适配器（注册顺序即应用顺序）
     */
    private static final List<IModAdapter> ADAPTERS = new ArrayList<>();

    /**
     * 已停用的适配器 id（类简单名）
     */
    private static final Set<String> DISABLED = new HashSet<>();

    /**
     * 单例标记
     */
    private static boolean initialized;

    /**
     * 注册适配器（内置 + 第三方共用入口）
     */
    public static void register(IModAdapter adapter)
    {
        if (adapter != null)
        {
            ADAPTERS.add(adapter);
        }
    }

    /**
     * 初始化内置适配器（AICore 底层启动时调用）
     */
    public static synchronized void initialize()
    {
        if (initialized)
        {
            return;
        }

        initialized = true;

        register(new EntitySourceAdapter());
        register(new CreateAdapter());
        register(new GeckoLibAdapter());
        register(new PlayerAnimationAdapter());
        register(new ShaderAdapter());

        System.out.println("[BBS AI] 已注册 " + ADAPTERS.size() + " 个 mod AI 兼容适配器");
    }

    /**
     * 适配器 id（类简单名，停用列表以此标识）
     */
    public static String getAdapterId(IModAdapter adapter)
    {
        return adapter.getClass().getSimpleName();
    }

    /**
     * 适配器是否启用
     */
    public static boolean isEnabled(IModAdapter adapter)
    {
        return !DISABLED.contains(getAdapterId(adapter));
    }

    /**
     * 设置适配器启用/停用（不负责持久化与重扫，由设置面板/回调统一做）
     */
    public static void setEnabled(IModAdapter adapter, boolean enabled)
    {
        if (enabled)
        {
            DISABLED.remove(getAdapterId(adapter));
        }
        else
        {
            DISABLED.add(getAdapterId(adapter));
        }
    }

    /**
     * 应用停用列表（逗号分隔 id，来自持久化设置），返回是否有变化
     */
    public static boolean applyDisabledList(String disabledList)
    {
        Set<String> next = new HashSet<>();

        if (disabledList != null)
        {
            for (String id : disabledList.split(","))
            {
                if (!id.isBlank())
                {
                    next.add(id.trim());
                }
            }
        }

        boolean changed = !next.equals(DISABLED);

        DISABLED.clear();
        DISABLED.addAll(next);

        return changed;
    }

    /**
     * 导出当前停用列表（持久化用）
     */
    public static String exportDisabledList()
    {
        return String.join(",", DISABLED);
    }

    /**
     * 对全部扫描结果应用适配器（扫描完成后调用一次；跳过被停用的适配器）
     */
    public static void applyAll(ModCompatScanner scanner, List<ModInfo> mods)
    {
        if (ADAPTERS.isEmpty())
        {
            return;
        }

        for (ModInfo info : mods)
        {
            for (IModAdapter adapter : ADAPTERS)
            {
                if (!isEnabled(adapter))
                {
                    continue;
                }

                if (!adapter.appliesToAll() && !adapter.modIds().contains(info.id))
                {
                    continue;
                }

                try
                {
                    StringBuilder builder = new StringBuilder();

                    adapter.describe(scanner, info, builder);

                    if (builder.length() > 0)
                    {
                        info.adapterNotes.add(builder.toString());
                    }
                }
                catch (Throwable throwable)
                {
                    /* 单个适配器失败不影响整体兼容扫描 */
                    System.err.println("[BBS AI] 适配器 " + adapter.title() + " 处理 " + info.id + " 失败（忽略）：" + throwable);
                }
            }
        }
    }

    /**
     * 适配器列表（UI 展示用）
     */
    public static List<IModAdapter> getAdapters()
    {
        return ADAPTERS;
    }

    /**
     * 简单 modid 集合工具
     */
    static Set<String> ids(String... values)
    {
        return new HashSet<>(Arrays.asList(values));
    }
}
