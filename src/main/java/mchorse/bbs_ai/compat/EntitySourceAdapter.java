package mchorse.bbs_ai.compat;

import java.util.Set;

import mchorse.bbs_ai.mods.ModCompatScanner;
import mchorse.bbs_ai.mods.ModInfo;

/**
 * 通用实体资源适配器（通配）
 *
 * <p>对所有新增实体的 mod 生效：列出实体 ID 样例，提示 AI「这些实体可以作为
 * BBS 演员（实体形态）直接入镜」。这是自动选角能力的基础 —— 不需要目标 mod
 * 做任何配合，属于零成本兼容。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class EntitySourceAdapter implements IModAdapter
{
    @Override
    public Set<String> modIds()
    {
        return ModAdapterManager.ids();
    }

    @Override
    public boolean appliesToAll()
    {
        return true;
    }

    @Override
    public String title()
    {
        return "实体演员库";
    }

    @Override
    public void describe(ModCompatScanner scanner, ModInfo info, StringBuilder out)
    {
        if (info.entities <= 0)
        {
            return;
        }

        out.append("提供 ").append(info.entities).append(" 种实体，可作为 BBS 演员形态（Morph）直接入镜；")
            .append("可用实体 ID：").append(String.join("、", info.entitySamples));
    }
}
