package mchorse.bbs_ai.mods;

import java.util.Locale;

/**
 * Mod 自动分类枚举
 *
 * <p>扫描器根据 modid / 名称 / 描述的关键词规则把未知 mod 归入大类，
 * 供 AI 上下文按类别汇总（避免逐条罗列撑爆提示词）。分类针对
 * 「BBS 电影拍摄」场景设计：与演员、动作、渲染、地形相关的类别权重更高。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public enum ModCategory
{
    ANIMATION("动画与骨骼", "playeranimator,emotecraft,figura,geckolib,animation,animate,pose,skeleton,bones,kirin,player-animation"),
    CINEMATIC("摄影与电影", "camera,cinematic,film,movie,replay,recorder,shader-free,shot,dolly,crane"),
    RENDERING("渲染与光影", "sodium,iris,oculus,optifine,shader,render,canvas,flywheel,lithium,particle,graphics,distant"),
    ENTITIES("生物与演员", "mob,creature,animal,monster,dragon,beast,fauna,zoology,pet,monster"),
    TECHNOLOGY("科技与机械", "create,tech,machine,industry,energy,pipe,conduit,automation,ae2,applied,flux,industrial"),
    MAGIC("魔法与神秘", "magic,arcane,spell,wizard,witch,ritual,mystic,sorcery,enchant"),
    DECORATION("装饰与建筑", "decor,furniture,deco,chipped,architect,building,builders,Macaw,stairs,interior,camp"),
    WORLDGEN("世界生成", "worldgen,biome,terralith,tectonic,terrain,dimension,structure,yung,atmosphere,landscape"),
    UTILITY("工具与信息", "jei,rei,emi,minimap,map,waystone,tooltip,waila,jade,top,inventory,sort,utility,jei-like"),
    LIBRARY("底层库", "fabric,api,library,core,lib,cloth,configly,architectury,yaml,kotlin,omega"),
    UNKNOWN("未分类", "");

    /**
     * 分类中文名（ digest 输出用）
     */
    public final String title;

    /**
     * 小写关键词表（逗号分隔，子串匹配）
     */
    private final String keywords;

    ModCategory(String title, String keywords)
    {
        this.title = title;
        this.keywords = keywords;
    }

    /**
     * 根据 modid / 名称 / 描述自动推断分类
     */
    public static ModCategory detect(String modId, String name, String description)
    {
        String haystack = ((modId == null ? "" : modId) + " "
            + (name == null ? "" : name) + " "
            + (description == null ? "" : description)).toLowerCase(Locale.ROOT);

        for (ModCategory category : values())
        {
            if (category == UNKNOWN)
            {
                continue;
            }

            for (String keyword : category.keywords.split(","))
            {
                if (!keyword.isEmpty() && haystack.contains(keyword))
                {
                    return category;
                }
            }
        }

        return UNKNOWN;
    }
}
