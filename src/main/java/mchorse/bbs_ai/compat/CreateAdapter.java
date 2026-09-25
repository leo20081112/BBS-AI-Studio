package mchorse.bbs_ai.compat;

import java.util.Set;

import mchorse.bbs_ai.mods.ModCompatScanner;
import mchorse.bbs_ai.mods.ModInfo;

/**
 * Create（机械动力）AI 兼容适配器
 *
 * <p>Create 是开源机械 mod 的代表作：旋转装置、列车、流水线都是天然的
 * 「动态布景」。本适配器把它对拍摄的价值翻译成 AI 可读说明，让分镜生成时
 * 大模型会主动考虑机械场景调度。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class CreateAdapter implements IModAdapter
{
    @Override
    public Set<String> modIds()
    {
        return ModAdapterManager.ids("create", "create-fabric", "createfabric");
    }

    @Override
    public String title()
    {
        return "Create 机械场景";
    }

    @Override
    public void describe(ModCompatScanner scanner, ModInfo info, StringBuilder out)
    {
        out.append("可搭建动态机械布景：齿轮传动、自动流水线、行驶中的列车（列车可作为移动机位载体）；")
            .append("机械结构运转时摄像机可用 BBS 追踪镜头跟随");

        /* create 附属联动提示 */
        if (scanner.byId("createaddition") != null || scanner.byId("createadditions") != null)
        {
            out.append("；检测到电力扩展，可补充近代工业布景");
        }
    }
}
