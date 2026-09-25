package mchorse.bbs_ai.integration;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import mchorse.bbs_ai.BBSAIStudio;
import mchorse.bbs_ai.ik.IKMode;
import mchorse.bbs_ai.preview.BakeMode;
import mchorse.bbs_ai.preview.PreviewSystem;
import mchorse.bbs_ai.storyboard.StoryboardScript;
import mchorse.bbs_ai.storyboard.StoryboardToFilmConverter;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 * {@code /bbs_ai} 命令树
 *
 * <p>子命令（项目规范模块 13）：
 * <ul>
 *   <li>{@code /bbs_ai generate storyboard <text>} —— 从文本生成分镜（离线转换；在线生成为客户端功能）</li>
 *   <li>{@code /bbs_ai preview enter|exit} —— 控制预览模式</li>
 *   <li>{@code /bbs_ai ik mode <native|blender>} —— 切换 IK 模式</li>
 *   <li>{@code /bbs_ai theme <classic|blender|mineimator>} —— 切换 UI 主题</li>
 *   <li>{@code /bbs_ai language <en_us|zh_cn|zh_tw>} —— 切换语言</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BBSAICommands
{
    /**
     * 注册命令【原版兼容】（与 BBSCommands 相同的注册模式）
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, net.minecraft.command.CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment)
    {
        LiteralArgumentBuilder<ServerCommandSource> bbsAi = CommandManager.literal("bbs_ai");

        /* /bbs_ai generate storyboard <text> —— 从 JSON 文本生成分镜并转换为影片 */
        LiteralArgumentBuilder<ServerCommandSource> generate = CommandManager.literal("generate");

        generate.then(CommandManager.literal("storyboard")
            .then(CommandManager.argument("text", StringArgumentType.greedyString()).executes(BBSAICommands::generateStoryboardFromJson)));

        /* /bbs_ai preview bake <overwrite|blend> */
        LiteralArgumentBuilder<ServerCommandSource> preview = CommandManager.literal("preview");

        preview.then(CommandManager.literal("enter").executes(BBSAICommands::previewEnter));
        preview.then(CommandManager.literal("exit").executes(BBSAICommands::previewExit));
        preview.then(CommandManager.literal("bake")
            .then(CommandManager.argument("mode", StringArgumentType.word()).executes(BBSAICommands::previewBake)));
        bbsAi.then(generate);
        bbsAi.then(preview);

        /* /bbs_ai ik mode <native|blender> */
        LiteralArgumentBuilder<ServerCommandSource> ik = CommandManager.literal("ik");
        LiteralArgumentBuilder<ServerCommandSource> ikMode = CommandManager.literal("mode");

        ikMode.then(CommandManager.literal("native").executes((ctx) -> setIkMode(ctx, IKMode.NATIVE)));
        ikMode.then(CommandManager.literal("blender").executes((ctx) -> setIkMode(ctx, IKMode.BLENDER)));
        ik.then(ikMode);
        bbsAi.then(ik);

        /* /bbs_ai theme <classic|blender|mineimator> */
        LiteralArgumentBuilder<ServerCommandSource> theme = CommandManager.literal("theme");

        theme.then(CommandManager.literal("classic").executes((ctx) -> setTheme(ctx, 0)));
        theme.then(CommandManager.literal("blender").executes((ctx) -> setTheme(ctx, 1)));
        theme.then(CommandManager.literal("mineimator").executes((ctx) -> setTheme(ctx, 2)));
        bbsAi.then(theme);

        /* /bbs_ai language <en_us|zh_cn|zh_tw> */
        LiteralArgumentBuilder<ServerCommandSource> language = CommandManager.literal("language");

        language.then(CommandManager.literal("en_us").executes((ctx) -> setLanguage(ctx, 0)));
        language.then(CommandManager.literal("zh_cn").executes((ctx) -> setLanguage(ctx, 1)));
        language.then(CommandManager.literal("zh_tw").executes((ctx) -> setLanguage(ctx, 2)));
        bbsAi.then(language);

        /* /bbs_ai version */
        bbsAi.then(CommandManager.literal("version").executes(BBSAICommands::version));

        /* /bbs_ai mods —— 查看 AI 对当前 mod 环境的理解（自动识别摘要） */
        bbsAi.then(CommandManager.literal("mods").executes(BBSAICommands::listMods));

        /* /bbs_ai undo / redo —— 撤销 / 重做最近一次 AI 操作 */
        bbsAi.then(CommandManager.literal("undo").executes((ctx) -> undoRedo(ctx, true)));
        bbsAi.then(CommandManager.literal("redo").executes((ctx) -> undoRedo(ctx, false)));

        dispatcher.register(bbsAi);
    }

    /**
     * /bbs_ai undo|redo：撤销/重做（须在主线程执行 Runnable，包一层 execute）
     */
    private static int undoRedo(CommandContext<ServerCommandSource> ctx, boolean undo)
    {
        net.minecraft.server.MinecraftServer server = ctx.getSource().getServer();

        server.execute(() ->
        {
            String description = undo
                ? mchorse.bbs_ai.core.AIUndoManager.get().undo()
                : mchorse.bbs_ai.core.AIUndoManager.get().redo();

            feedback(ctx, description == null
                ? (undo ? "没有可撤销的 AI 操作" : "没有可重做的 AI 操作")
                : (undo ? "已撤销：" : "已重做：") + description);
        });

        return 1;
    }

    /**
     * /bbs_ai mods：输出 mod 自动识别摘要（命中知识库的重点 mod）
     */
    private static int listMods(CommandContext<ServerCommandSource> ctx)
    {
        mchorse.bbs_ai.mods.ModCompatScanner scanner = mchorse.bbs_ai.mods.ModCompatScanner.get();

        feedback(ctx, scanner.summaryLine());

        int shown = 0;

        for (mchorse.bbs_ai.mods.ModInfo info : scanner.getMods())
        {
            if (!info.isKnown() || shown >= 6)
            {
                continue;
            }

            feedback(ctx, "· " + info.knowledge.name + "（" + info.id + "）：" + info.knowledge.aiHint);
            shown++;
        }

        feedback(ctx, "完整报告见 AI 工具面板 → Mod 兼容环境 → 导出 AI 知识库");

        return 1;
    }

    /**
     * 从 JSON 文本解析分镜脚本并转换为影片（离线流程；在线生成为客户端功能）
     */
    private static int generateStoryboardFromJson(CommandContext<ServerCommandSource> ctx)
    {
        String text = StringArgumentType.getString(ctx, "text");

        try
        {
            StoryboardScript script = StoryboardScript.fromJson(text);
            Film film = new StoryboardToFilmConverter().convert(script);
            mchorse.bbs_mod.data.types.MapType data = (mchorse.bbs_mod.data.types.MapType) film.toData();
            String name = "ai_storyboard_" + System.currentTimeMillis() / 1000;

            BBSMod.getFilms().create(name, data);

            /* 与编辑器动作一致：登记撤销/重做 */
            mchorse.bbs_ai.core.AIUndoManager.get().record("film", "生成影片 " + name,
                () -> BBSMod.getFilms().delete(name),
                () -> BBSMod.getFilms().create(name, data));

            return feedback(ctx, "分镜已转换为影片：" + name + "（" + script.shots.size() + " 个镜头，可 /bbs_ai undo 撤销）");
        }
        catch (Exception e)
        {
            return feedback(ctx, "分镜转换失败：" + e.getMessage());
        }
    }

    /**
     * 进入预览模式（仅在有暂存数据时有效）
     */
    private static int previewEnter(CommandContext<ServerCommandSource> ctx)
    {
        PreviewSystem system = PreviewSystem.get();

        if (system.getCache().size() == 0)
        {
            return feedback(ctx, "当前没有暂存的预览数据（先用 AI 生成或导入动作）");
        }

        return feedback(ctx, "预览模式已就绪（" + system.getCache().size() + " 条预览轨道）");
    }

    /**
     * 退出预览模式（放弃全部预览数据）
     */
    private static int previewExit(CommandContext<ServerCommandSource> ctx)
    {
        PreviewSystem.get().discard();

        return feedback(ctx, "已放弃全部预览数据");
    }

    /**
     * /bbs_ai preview bake [overwrite|blend] —— 烘焙预览数据
     */
    static int previewBake(CommandContext<ServerCommandSource> ctx)
    {
        String mode = StringArgumentType.getString(ctx, "mode");
        BakeMode bakeMode = "blend".equalsIgnoreCase(mode) ? BakeMode.INSERT_BLEND : BakeMode.OVERWRITE;

        int written = PreviewSystem.get().bake(bakeMode);

        return feedback(ctx, "烘焙完成：写入 " + written + " 个关键帧");
    }

    /**
     * 切换 IK 模式
     */
    private static int setIkMode(CommandContext<ServerCommandSource> ctx, IKMode mode)
    {
        ValueInt setting = mchorse.bbs_ai.core.BBSAISettings.ikMode;
        IKMode old = setting.get() == 0 ? IKMode.NATIVE : IKMode.BLENDER;

        setting.set(mode == IKMode.NATIVE ? 0 : 1);

        BBSMod.events.post(new mchorse.bbs_ai.integration.event.IKModeChangeEvent(mode, old));

        return feedback(ctx, "IK 模式已切换为：" + (mode == IKMode.BLENDER ? "Blender 风格" : "原生 IK"));
    }

    /**
     * 切换主题（写设置，客户端监听生效）
     */
    private static int setTheme(CommandContext<ServerCommandSource> ctx, int themeIndex)
    {
        mchorse.bbs_ai.core.BBSAISettings.uiTheme.set(themeIndex);

        return feedback(ctx, "主题已切换（客户端界面刷新后生效）");
    }

    /**
     * 切换语言（写设置，客户端监听生效）
     */
    private static int setLanguage(CommandContext<ServerCommandSource> ctx, int languageIndex)
    {
        mchorse.bbs_ai.core.BBSAISettings.uiLanguage.set(languageIndex);

        return feedback(ctx, "语言已切换（客户端界面刷新后生效）");
    }

    /**
     * 版本信息
     */
    private static int version(CommandContext<ServerCommandSource> ctx)
    {
        return feedback(ctx, "BBS AI Studio v" + BBSAIStudio.VERSION + "（格式 " + BBSAIStudio.MOTION_FORMAT + "）");
    }

    /**
     * 发送命令反馈
     */
    private static int feedback(CommandContext<ServerCommandSource> ctx, String message)
    {
        ctx.getSource().sendMessage(Text.literal("[BBS AI] " + message));

        return 1;
    }
}
