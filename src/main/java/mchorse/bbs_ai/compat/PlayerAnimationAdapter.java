package mchorse.bbs_ai.compat;

import java.util.Set;

import mchorse.bbs_ai.mods.ModCompatScanner;
import mchorse.bbs_ai.mods.ModInfo;

/**
 * 玩家动画类 mod AI 兼容适配器
 *
 * <p>覆盖 playerAnimator / Emotecraft / Figura 等开源玩家动画 mod：
 * 它们让真人玩家形态的演员能做出超出演示录像关键帧的动作（翻滚、表情、
 * 自定义 Avatar），是 BBS 视频识别动作之外的补充动作来源。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class PlayerAnimationAdapter implements IModAdapter
{
    @Override
    public Set<String> modIds()
    {
        return ModAdapterManager.ids("playeranimator", "player-animator", "emotecraft", "figura", "skinshuffle");
    }

    @Override
    public String title()
    {
        return "玩家动画扩展";
    }

    @Override
    public void describe(ModCompatScanner scanner, ModInfo info, StringBuilder out)
    {
        if ("figura".equals(info.id) || "skinshuffle".equals(info.id))
        {
            out.append("玩家外形高度可定制（Avatar/皮肤切换），适合非常规角色造型入镜");
        }
        else
        {
            out.append("玩家演员可播放库内动画（翻滚/攀爬/表情等），可作为 BBS 关键帧动画的补充动作源");
        }
    }
}
