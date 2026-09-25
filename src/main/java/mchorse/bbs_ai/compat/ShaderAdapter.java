package mchorse.bbs_ai.compat;

import java.util.Set;

import mchorse.bbs_ai.mods.ModCompatScanner;
import mchorse.bbs_ai.mods.ModInfo;

/**
 * 光影/渲染 mod AI 兼容适配器
 *
 * <p>覆盖 Iris / Oculus / Sodium 等开源渲染 mod：向 AI 说明当前拍摄环境的
 * 渲染能力（有无光影、性能余量），影响其对画面氛围与镜头量级的建议。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ShaderAdapter implements IModAdapter
{
    @Override
    public Set<String> modIds()
    {
        return ModAdapterManager.ids("iris", "oculus", "sodium", "vanilla");
    }

    @Override
    public String title()
    {
        return "光影渲染环境";
    }

    @Override
    public void describe(ModCompatScanner scanner, ModInfo info, StringBuilder out)
    {
        boolean shaderLoader = "iris".equals(info.id) || "oculus".equals(info.id);

        if (shaderLoader)
        {
            out.append("光影可用：建议分镜标注时间段（黄金时段/夜晚）与天气氛围，由光影渲染画面质感");
        }
        else
        {
            out.append("渲染性能优化：大场面多人同屏与高帧率录制（BBS 慢动作）更有余量");
        }
    }
}
