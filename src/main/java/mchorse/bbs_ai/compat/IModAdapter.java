package mchorse.bbs_ai.compat;

import java.util.Set;

import mchorse.bbs_ai.mods.ModCompatScanner;
import mchorse.bbs_ai.mods.ModInfo;

/**
 * Mod → AI 兼容适配器接口
 *
 * <p>为主流开源 mod 提供「AI 兼容版本」的扩展点：适配器在扫描命中目标 mod 时，
 * 向 {@link ModInfo#adapterNotes} 追加 AI 可读的能力说明，让大模型在生成
 * 分镜 / 回答问题时理解该 mod 提供的拍摄能力。</p>
 *
 * <p>实现要求：
 * <ul>
 *   <li><b>不得硬依赖目标 mod 的类</b> —— 只通过 modid 判断与注册表字符串工作，
 *       目标 mod 缺席时适配器静默不生效（兼容性第一）</li>
 *   <li>describe 里只写「对拍摄有用的事实」，不要广告词</li>
 * </ul></p>
 *
 * <p>第三方为自己的 mod 开发 AI 兼容：实现本接口并调用
 * {@link ModAdapterManager#register} 注册即可。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public interface IModAdapter
{
    /**
     * 适配的 modid 集合（小写；命中任一即应用本适配器）
     */
    Set<String> modIds();

    /**
     * 是否对全部 mod 生效（通配适配器；describe 内自行按 ModInfo 条件过滤）
     */
    default boolean appliesToAll()
    {
        return false;
    }

    /**
     * 适配器名称（日志与 UI 展示）
     */
    String title();

    /**
     * 向 out 追加该 mod 的 AI 能力说明（扫描命中后调用）
     *
     * @param scanner 扫描器（可查询其它 mod / 注册表统计）
     * @param info    命中的 mod 信息（已含知识库与统计）
     * @param out     输出缓冲
     */
    void describe(ModCompatScanner scanner, ModInfo info, StringBuilder out);
}
