package mchorse.bbs_ai.mods;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 主流开源 mod 知识库
 *
 * <p>内置收录常见开源 mod 的「拍摄视角」知识卡片：它是什么、能提供什么能力、
 * 对 BBS 电影拍摄有什么用。扫描命中 modid 后随 {@link ModInfo} 一起进入 AI 上下文，
 * 让大模型在生成分镜/回答问题时「认识」这些 mod。未收录的 mod 走关键词自动分类。</p>
 *
 * <p>第三方可以通过 {@link #register} 追加知识卡片（为自己开发的 mod 做 AI 识别）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ModKnowledgeBase
{
    /**
     * 知识卡片：modid → 知识
     */
    private static final Map<String, Knowledge> KNOWLEDGE = new HashMap<>();

    static
    {
        /* ---- 动画 / 演员（对拍摄最重要） ---- */
        register("geckolib", "GeckoLib", ModCategory.ANIMATION,
            "通用的 Java/Bedrock 几何模型动画引擎，大量 mod 的生物与机械动画基于它驱动。",
            "依赖它的 mod 生物自带流畅骨骼动画，是优质的演员与背景素材来源。",
            "骨骼动画引擎", "自定义模型渲染");
        register("playeranimator", "Player Animator", ModCategory.ANIMATION,
            "玩家第一/第三人称骨骼动画库。",
            "玩家角色可以做翻滚、攀爬等自定义动作，适合动作戏替身。",
            "玩家骨骼动画");
        register("emotecraft", "Emotecraft", ModCategory.ANIMATION,
            "玩家表情/动作动画 mod，支持导入社区动作。",
            "可直接给演员套用现成表情动作（挥手、跳舞等），省去关键帧制作。",
            "玩家动作库", "社区动画导入");
        register("figura", "Figura", ModCategory.ANIMATION,
            "高度可定制的玩家模型（Lua 驱动 Avatar）。",
            "配合 BBS 自定义模型可做出非常规角色造型。",
            "Lua 玩家模型");

        /* ---- 渲染 / 光影 ---- */
        register("sodium", "Sodium", ModCategory.RENDERING,
            "现代渲染引擎优化（帧率提升）。",
            "大型场景多人同屏拍摄时建议开启，与光影加载器配合使用。",
            "渲染优化");
        register("iris", "Iris", ModCategory.RENDERING,
            "光影包加载器（OptiFine 光影兼容）。",
            "电影质感的核心：配合光影包拍摄，注意 BBS 色度键抠像与后处理顺序。",
            "光影加载");
        register("oculus", "Oculus", ModCategory.RENDERING,
            "Iris 的 Forge/NeoForge 对应实现。",
            "同 Iris：光影拍摄注意事项一致。",
            "光影加载");
        register("flywheel", "Flywheel", ModCategory.RENDERING,
            "实例化渲染引擎（Create 御用）。",
            "影响机械结构的渲染表现，拍摄 Create 场景时保留。",
            "实例化渲染");

        /* ---- 科技 / 机械 ---- */
        register("create", "Create", ModCategory.TECHNOLOGY,
            "机械动力：旋转力驱动的机械、火车、工厂自动化。",
            "绝佳的动态场景素材：旋转齿轮、行驶列车、流水线都能当「群众演员」；实体化结构可被镜头捕捉。",
            "动态机械", "列车", "自动化流水线");
        register("createaddition", "Create Crafts & Additions", ModCategory.TECHNOLOGY,
            "Create 的电力扩展。",
            "补充电线杆/电动机等近代工业布景。",
            "电力机械");
        register("createdeco", "Create Deco", ModCategory.DECORATION,
            "Create 风格装饰方块。",
            "工业风场景搭建素材。",
            "工业装饰方块");

        /* ---- 生物 / 演员 ---- */
        register("alexsmobs", "Alex's Mobs", ModCategory.ENTITIES,
            "添加大量高质量原创生物（90+），均带精致动画。",
            "重要演员库：如座头鲸、螳螂虾等都可入镜；实体 ID 前缀 alexsmobs:。",
            "原创生物", "高质量动画");
        register("naturalist", "Naturalist", ModCategory.ENTITIES,
            "生态化生物群系动物。",
            "自然环境动物演员（蛇、熊、啄木鸟等）。",
            "生态动物");
        register("friendsandfoes", "Friends & Foes", ModCategory.ENTITIES,
            "补充投票落选生物（恶霸等）。",
            "额外生物演员。",
            "原版风格生物");

        /* ---- 世界 / 场景 ---- */
        register("terralith", "Terralith", ModCategory.WORLDGEN,
            "基于原版方块的地形重做（数据包式）。",
            "外景地库：峡谷、火山、浮岛等大场面取景地，无需额外方块。",
            "大场面地形");
        register("biomesoplenty", "Biomes O' Plenty", ModCategory.WORLDGEN,
            "经典生物群系扩展。",
            "多样化外景地（红木林、薰衣草田等）。",
            "群系扩展");
        register("tectonic", "Tectonic", ModCategory.WORLDGEN,
            "大型山脉地形生成。",
            "史诗山脉背景。",
            "山脉地形");

        /* ---- 工具 / 信息 ---- */
        register("jei", "Just Enough Items", ModCategory.UTILITY,
            "物品/配方查询界面。",
            "拍摄辅助：快速找道具（不直接入镜）。",
            "配方查询");
        register("rei", "Roughly Enough Items", ModCategory.UTILITY,
            "Fabric 侧物品/配方查询。",
            "同 JEI。",
            "配方查询");
        register("emi", "EMI", ModCategory.UTILITY,
            "轻量物品/配方查询。",
            "同 JEI。",
            "配方查询");
        register("xaerominimap", "Xaero's Minimap", ModCategory.UTILITY,
            "小地图与 waypoints。",
            "勘景工具：标记机位与演员站位。",
            "小地图", "路标");
        register("journeymap", "JourneyMap", ModCategory.UTILITY,
            "全屏地图与雷达。",
            "勘景与场面调度辅助。",
            "全屏地图");

        /* ---- 底层库 ---- */
        register("fabric-api", "Fabric API", ModCategory.LIBRARY,
            "Fabric 基础 API 库。", "运行依赖，无拍摄影响。", "基础库");
        register("cloth-config", "Cloth Config", ModCategory.LIBRARY,
            "配置界面库。", "运行依赖。", "配置库");
        register("architectury", "Architectury", ModCategory.LIBRARY,
            "跨平台 mod 开发库。", "运行依赖。", "开发库");
        register("modmenu", "Mod Menu", ModCategory.UTILITY,
            "mod 列表与配置入口。", "排查环境用。", "mod 管理");
    }

    /**
     * 注册/覆盖一张知识卡片（第三方 addon 亦可通过此入口为自己的 mod 提供 AI 知识）
     */
    public static void register(String modId, String name, ModCategory category, String summary, String aiHint, String... capabilities)
    {
        KNOWLEDGE.put(modId, new Knowledge(name, category, summary, aiHint, Arrays.asList(capabilities)));
    }

    /**
     * 按 modid 查询知识卡片（不存在返回 null）
     */
    public static Knowledge lookup(String modId)
    {
        return modId == null ? null : KNOWLEDGE.get(modId);
    }

    /**
     * 知识卡片数据
     */
    public static class Knowledge
    {
        /**
         * 通行名称
         */
        public final String name;

        /**
         * 分类
         */
        public final ModCategory category;

        /**
         * 一句话简介（它是什么）
         */
        public final String summary;

        /**
         * 拍摄视角提示（对 BBS 电影有什么用）
         */
        public final String aiHint;

        /**
         * 能力标签列表
         */
        public final List<String> capabilities;

        public Knowledge(String name, ModCategory category, String summary, String aiHint, List<String> capabilities)
        {
            this.name = name;
            this.category = category;
            this.summary = summary;
            this.aiHint = aiHint;
            this.capabilities = capabilities;
        }
    }
}
