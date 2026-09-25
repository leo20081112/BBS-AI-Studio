package mchorse.bbs_ai.compat;

import java.util.Set;

import mchorse.bbs_ai.mods.ModCompatScanner;
import mchorse.bbs_ai.mods.ModInfo;

/**
 * GeckoLib AI 兼容适配器
 *
 * <p>GeckoLib 是开源骨骼动画引擎，大量生物 mod 基于它驱动动画。
 * 安装了 GeckoLib 意味着场上生物演员的动作通常是骨骼级的流畅动画，
 * AI 生成分镜时可以放心安排生物的动作戏（奔跑、攻击、飞行）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class GeckoLibAdapter implements IModAdapter
{
    @Override
    public Set<String> modIds()
    {
        return ModAdapterManager.ids("geckolib", "geckolib3", "geckolib-fabric");
    }

    @Override
    public String title()
    {
        return "GeckoLib 骨骼动画";
    }

    @Override
    public void describe(ModCompatScanner scanner, ModInfo info, StringBuilder out)
    {
        out.append("骨骼动画引擎已就位：依赖它的生物演员自带流畅动作（待机/移动/攻击等），")
            .append("分镜中可放心安排生物动作戏，无需逐帧 K 动画");
    }
}
