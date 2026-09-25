package mchorse.bbs_ai.ui.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mchorse.bbs_ai.core.AIServiceManager;
import mchorse.bbs_ai.core.AIUndoManager;
import mchorse.bbs_ai.core.api.APIException;
import mchorse.bbs_ai.format.MotionData;
import mchorse.bbs_ai.format.MotionFrame;
import mchorse.bbs_ai.import_manager.SourceType;
import mchorse.bbs_ai.preview.PreviewSystem;
import mchorse.bbs_ai.storyboard.StoryboardScript;
import mchorse.bbs_ai.storyboard.StoryboardToFilmConverter;
import mchorse.bbs_ai.ui.editor.AIChatSession.Message;
import mchorse.bbs_ai.ui.editor.AIChatSession.Role;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * AI 编辑器面板（专门的 AI 动画生成界面）
 *
 * <p>左右分栏布局：
 * <ul>
 *   <li><b>左侧（动画界面）</b>：透明视口直接观察游戏世界与暂存预览，
 *       底部为关键帧时间轴（可视化 PreviewSystem 暂存轨道，点击选帧）</li>
 *   <li><b>右侧（对话与思维链）</b>：上半为对话区（气泡 + 动作按钮），
 *       下半为思维链 / 工具调用日志（AI 正在做什么、调用结果），
 *       底部快捷指令 + 输入框（Ctrl+Enter 发送）</li>
 * </ul></p>
 *
 * <p>顶栏：AI 设置（服务商 / 生成参数 / 人物模型映射 / 插件调用）、
 * 撤销 / 重做（Ctrl+Z / Ctrl+Y，AI 操作全部可逆）、状态提示。</p>
 *
 * <p>AI 回复中的 ```storyboard / ```motion 代码块渲染成
 * 「生成为影片 / 预览动作」按钮，执行结果进工具日志并登记撤销。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class UIAIEditorPanel extends UIDashboardPanel
{
    /**
     * storyboard 代码块协议：```storyboard ... ```
     */
    private static final Pattern STORYBOARD_BLOCK = Pattern.compile("```storyboard\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /**
     * motion 代码块协议：```motion ... ```
     */
    private static final Pattern MOTION_BLOCK = Pattern.compile("```motion\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    /**
     * 通用代码块（用于分段渲染，Codex 风格暗盒）
     */
    private static final Pattern CODE_BLOCK = Pattern.compile("```([a-zA-Z0-9_]*)\\s*([\\s\\S]*?)```");

    /**
     * 暂存轨道的缓存 key（replay "0"，与工具面板一致）
     */
    private static final String STAGE_KEY = "|0|";

    /**
     * 左右分栏比例（左 56%）
     */
    private static final float SPLIT = 0.56F;

    /**
     * 会话
     */
    private final AIChatSession session;

    /* ---- 右侧：对话 ---- */
    private UIScrollView messageList;
    private UITextarea input;
    private UILabel thinking;

    /* ---- 右侧：思维链 / 工具调用 ---- */
    private final AIToolCallLog toolLog = new AIToolCallLog();

    /* ---- 左侧：画面窗口与影片时间轴 ---- */
    private final AIViewport viewport = new AIViewport();
    private final AIFilmTimeline timeline;

    /* ---- 顶栏 ---- */
    private UILabel status;

    /**
     * 重建后待滚动到底部
     */
    private boolean pendingScroll;

    /* ==================================================================== */

    public UIAIEditorPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.session = new AIChatSession(dashboard);
        this.timeline = new AIFilmTimeline(this::getCurrentFilm, (clip) -> this.viewport.setSelectedClip(clip));

        /* ---- 顶栏：深色底条（透明面板上保证图标/状态可读）+ AI 设置 / 撤销 / 重做 / 状态 ---- */
        UIElement topShade = new ShadedColumn();

        topShade.relative(this).xy(0, 0).w(1F).h(20);

        UIElement topBar = UI.row(2,
            this.iconButton(Icons.GEAR, "bbs_ai.editor.settings", this::openSettings),
            this.iconButton(Icons.UNDO, "bbs_ai.editor.undo", this::undo),
            this.iconButton(Icons.REDO, "bbs_ai.editor.redo", this::redo)
        );

        topBar.relative(topShade).xy(0, 0).w(0.3F).h(18);

        this.status = UI.label(IKey.constant(" "), 12, Colors.GRAY);
        this.status.relative(topShade).x(0.3F).y(3).w(0.68F).h(12);

        topShade.add(topBar, this.status);

        /* ---- 左侧：视口 + 时间轴（面板透明，世界即为预览背景） ---- */
        this.viewport.relative(this).x(0).y(22).w(SPLIT).h(1F, -66);
        this.timeline.relative(this).x(0).y(1F, -40).w(SPLIT).h(36);

        /* ---- 右侧：深色栏容器（对话 + 工具日志 + 快捷指令 + 输入） ---- */
        ShadedColumn right = new ShadedColumn();

        right.relative(this).x(SPLIT).y(20).w(1F - SPLIT).h(1F, -20);

        this.messageList = UI.scrollView();
        this.messageList.relative(right).x(4).y(4).w(1F, -8).h(1F, -248);

        this.toolLog.relative(right).x(4).y(1F, -244).w(1F, -8).h(156);

        /* 快捷指令 2×2：手动定位（与 send 按钮同模式，避免嵌套 resizer 宽度退化） */
        UIButton quickAnalyze = this.quickButton("bbs_ai.editor.quick.analyze", this::quickAnalyze);
        UIButton quickStoryboard = this.quickButton("bbs_ai.editor.quick.storyboard", this::quickStoryboard);
        UIButton quickCamera = this.quickButton("bbs_ai.editor.quick.camera", this::quickCamera);
        UIButton quickClear = this.quickButton("bbs_ai.editor.quick.clear", this::quickClear);

        quickAnalyze.relative(right).x(4).y(1F, -84).w(0.5F, -8).h(16);
        quickStoryboard.relative(right).x(0.5F).y(1F, -84).w(0.5F, -8).h(16);
        quickCamera.relative(right).x(4).y(1F, -64).w(0.5F, -8).h(16);
        quickClear.relative(right).x(0.5F).y(1F, -64).w(0.5F, -8).h(16);

        this.input = new UITextarea((text) -> {});
        this.input.background().wrap();
        this.input.relative(right).x(4).y(1F, -46).w(1F, -84).h(42);

        UIButton send = new UIButton(L10n.lang("bbs_ai.editor.send"), (b) -> this.send());
        send.relative(right).x(1F, -76).y(1F, -38).w(72).h(18);
        send.tooltip(L10n.lang("bbs_ai.editor.send.tooltip"));

        /* 右栏子元素统一挂在 right 容器下（relative 目标必须等于实际父级，
         * 否则 resizer 与父布局协作失效 —— 上游 UIOverlayPanel.content 同模式） */
        right.add(this.messageList, this.toolLog, quickAnalyze, quickStoryboard, quickCamera, quickClear, this.input, send);

        this.add(topShade, this.viewport, this.timeline, right);

        /* 键位：Ctrl+Enter 发送 / Ctrl+Z 撤销 / Ctrl+Y 重做（上游组合键） */
        this.keys().register(Keys.CONFIRM, this::send);
        this.keys().register(Keys.UNDO, this::undo);
        this.keys().register(Keys.REDO, this::redo);

        this.rebuildMessages();
    }

    /**
     * 顶栏图标按钮
     */
    private UIIcon iconButton(Icon icon, String tooltipKey, Runnable action)
    {
        UIIcon button = new UIIcon(icon, (b) -> action.run());

        button.tooltip(L10n.lang(tooltipKey));

        return button;
    }

    /**
     * 快捷指令按钮
     */
    private UIButton quickButton(String key, Runnable action)
    {
        /* 宽高由调用方链式指定（预先 h() 会干扰后续分数宽度设置） */
        return new UIButton(L10n.lang(key), (b) -> action.run());
    }

    /* ====================================================================
     * 顶栏动作
     * ==================================================================== */

    /**
     * 面板透明（与影片面板一致）：左侧直接观察世界与预览，右栏自带深色底
     */
    @Override
    public boolean needsBackground()
    {
        return false;
    }

    /**
     * 打开 AI 设置界面（服务商 / 生成参数 / 人物模型映射 / 插件调用）
     */
    private void openSettings()
    {
        UIOverlay.addOverlay(this.getContext(), new AISettingsOverlayPanel(), 420, 440);
    }

    /**
     * 撤销最近一次 AI 操作
     */
    private void undo()
    {
        String description = AIUndoManager.get().undo();

        this.toolLog.info(description == null
            ? L10n.lang("bbs_ai.editor.undo.empty").get()
            : L10n.lang("bbs_ai.editor.undo.done").format(description).get());
    }

    /**
     * 重做
     */
    private void redo()
    {
        String description = AIUndoManager.get().redo();

        this.toolLog.info(description == null
            ? L10n.lang("bbs_ai.editor.redo.empty").get()
            : L10n.lang("bbs_ai.editor.redo.done").format(description).get());
    }

    /* ====================================================================
     * 消息渲染
     * ==================================================================== */

    private void rebuildMessages()
    {
        this.messageList.removeAll();

        for (Message message : this.session.getHistory())
        {
            this.addMessageCards(message);
        }

        if (this.thinking != null)
        {
            this.messageList.add(this.thinking);
        }

        this.pendingScroll = true;
    }

    /**
     * 把一条消息按 ``` 代码块分段渲染成 Codex 风格卡片序列：
     * 叙述段（角色标头 + 正文）与代码段（语言标头 + 暗盒）交替，
     * 动作按钮挂在整条消息末尾。
     */
    private void addMessageCards(Message message)
    {
        int headerColor = ChatStyle.headerColor(message.role);
        int bodyColor = ChatStyle.bodyColor(message.role);
        int accent = ChatStyle.accentColor(message.role);
        String header = ChatStyle.header(message.role);

        Matcher block = CODE_BLOCK.matcher(message.text);
        int last = 0;

        while (block.find())
        {
            /* 代码块前的叙述段 */
            if (block.start() > last)
            {
                String prose = message.text.substring(last, block.start()).trim();

                if (!prose.isEmpty())
                {
                    this.messageList.add(new AITextCard(header, prose, headerColor, bodyColor, 0x26000000, accent));
                    header = null; /* 同一角色后续卡片不再重复标头 */
                }
            }

            /* 代码块本体（语言标头 + 暗盒） */
            String lang = block.group(1).isEmpty() ? L10n.lang("bbs_ai.editor.code.anon").get() : block.group(1);
            String code = block.group(2).trim();

            this.messageList.add(new AITextCard(lang, code, 0xFF6E86A8, 0xFFB9C4D6, 0xF2080A10, 0));

            last = block.end();
        }

        /* 末尾剩余叙述段 */
        if (last < message.text.length())
        {
            String prose = message.text.substring(last).trim();

            if (!prose.isEmpty())
            {
                this.messageList.add(new AITextCard(header, prose, headerColor, bodyColor, 0x26000000, accent));
            }
        }

        /* 动作按钮（storyboard / motion 代码块 → 可执行操作） */
        for (Runnable action : this.extractActions(message))
        {
            UIButton button = new UIButton(IKey.constant("▶ " + action.toString()), (b) -> action.run());

            button.h(18);
            this.messageList.add(button);
        }
    }

    @Override
    public void update()
    {
        super.update();

        if (this.pendingScroll)
        {
            this.messageList.scroll.setScroll(Integer.MAX_VALUE);
            this.pendingScroll = false;
        }

        /* 状态栏：最近 AI 操作 + 可撤销提示 */
        if (AIUndoManager.get().canUndo())
        {
            this.status.label = IKey.constant("⟲ " + AIUndoManager.get().lastDescription() + "（Ctrl+Z 撤销）");
        }
        else
        {
            this.status.label = IKey.constant(" ");
        }
    }

    /* ====================================================================
     * 发送与接收
     * ==================================================================== */

    private void send()
    {
        String text = this.input.getText().trim();

        if (text.isEmpty())
        {
            return;
        }

        if (!AIServiceManager.get().isApiMode())
        {
            this.session.add(Role.ERROR, L10n.lang("bbs_ai.editor.need_api").get());
            this.rebuildMessages();

            return;
        }

        this.session.add(Role.USER, text);
        this.input.setText("");
        this.thinking = UI.label(L10n.lang("bbs_ai.editor.thinking"), 12, Colors.GRAY);
        this.rebuildMessages();

        AIToolCallLog.Entry call = this.toolLog.begin("调用 AI 服务（" + AIServiceManager.get().getConfig().getModel() + "）");

        AIServiceManager.get().generateAsync(
            this.session.buildSystemPrompt(),
            this.session.buildUserPrompt(text),
            null,
            (result) -> this.onAIResponse(result, call),
            (error) -> this.onAIError(error, call)
        );
    }

    private void onAIResponse(String result, AIToolCallLog.Entry call)
    {
        net.minecraft.client.MinecraftClient.getInstance().execute(() ->
        {
            this.thinking = null;
            this.toolLog.complete(call, true, result.length() + " 字");

            this.session.add(Role.ASSISTANT, result);
            this.rebuildMessages();

            List<Runnable> actions = this.extractActions(this.session.getHistory().get(this.session.getHistory().size() - 1));

            if (!actions.isEmpty())
            {
                this.toolLog.info("解析出 " + actions.size() + " 个可执行动作（见对话内按钮）");
            }
        });
    }

    private void onAIError(APIException error, AIToolCallLog.Entry call)
    {
        net.minecraft.client.MinecraftClient.getInstance().execute(() ->
        {
            this.thinking = null;
            this.toolLog.complete(call, false, error.getUserMessage());

            this.session.add(Role.ERROR, error.getUserMessage());
            this.rebuildMessages();
        });
    }

    /* ====================================================================
     * 快捷指令
     * ==================================================================== */

    /**
     * 注入演示对话（仅调试桥自动化测试用：长文本换行 + 代码块 + 动作按钮 + 工具日志）
     */
    public void seedDemoForTesting()
    {
        StringBuilder longText = new StringBuilder();

        longText.append("建议采用「肩后推近」的开场：摄像机从角色右肩后方缓缓前推，配合轻微呼吸感晃动，\n");
        longText.append("在第 8 tick 左右越过肩线露出面部特写，随后上摇看向天空完成情绪释放。\n");
        longText.append("这一段需要注意三点：一是推近速度保持匀速避免突兀；二是上摇终点预留天空留白；三是全片色温偏暖以贴合黄昏氛围。");

        this.session.add(Role.USER, "帮我设计一个 20 tick 的镜头：先推近角色，再看向天空");
        this.session.add(Role.ASSISTANT, longText + "\n\n```storyboard\n{\"title\":\"demo\",\"shots\":[{\"type\":\"camera\",\"position\":[81,80,0],\"point\":[81,75,0],\"duration\":10},{\"type\":\"camera\",\"position\":[81,78,-6],\"point\":[81,95,0],\"duration\":10}]}\n```\n\n点击下方按钮即可一键生成为影片（可 Ctrl+Z 撤销）。");
        this.thinking = null;
        this.rebuildMessages();

        this.toolLog.info("演示模式：已注入示例对话（含长文本与代码块）");
        AIToolCallLog.Entry call = this.toolLog.begin("调用 AI 服务（demo）");
        this.toolLog.complete(call, true, "演示响应 512 字");
    }

    /**
     * 环境分析（本地，无需 API）：把 mod 自动识别摘要直接注入对话
     */
    public void quickAnalyze()
    {
        String digest;

        try
        {
            digest = mchorse.bbs_ai.mods.AIContextService.get().buildModDigest(true);
        }
        catch (Exception e)
        {
            digest = "环境分析失败：" + e.getMessage();
        }

        this.session.add(Role.SYSTEM, L10n.lang("bbs_ai.editor.analyze.result").get() + "\n\n" + digest);
        this.toolLog.info("环境分析完成（本地识别）");
        this.rebuildMessages();
    }

    /**
     * 填入分镜草稿模板
     */
    public void quickStoryboard()
    {
        this.input.setText(L10n.lang("bbs_ai.editor.quick.storyboard.template").get());
    }

    /**
     * 填入运镜建议模板
     */
    public void quickCamera()
    {
        this.input.setText(L10n.lang("bbs_ai.editor.quick.camera.template").get());
    }

    /**
     * 清空对话
     */
    public void quickClear()
    {
        this.session.clear();
        this.rebuildMessages();
        this.toolLog.info("对话已清空");
    }

    /* ====================================================================
     * 内部控件
     * ==================================================================== */

    /**
     * 当前打开的影片（影片面板 getData；未打开返回 null）
     */
    private Film getCurrentFilm()
    {
        mchorse.bbs_mod.ui.film.UIFilmPanel panel = this.dashboard.getPanel(mchorse.bbs_mod.ui.film.UIFilmPanel.class);

        return panel == null ? null : panel.getData();
    }

    /**
     * 画面窗口（左侧）：透明观察游戏世界与演员/预览实况，
     * 叠加当前影片信息与时间轴选中剪辑提示
     */
    private class AIViewport extends UIElement
    {
        /**
         * 时间轴选中的剪辑序号（-1 = 未选）
         */
        private int selectedClip = -1;

        public void setSelectedClip(int clip)
        {
            this.selectedClip = clip;
        }

        @Override
        public void render(UIContext context)
        {
            int x1 = this.area.x + 4;
            int y1 = this.area.y + 4;
            int x2 = this.area.ex() - 4;
            int y2 = this.area.ey() - 4;

            /* 现代取景框：四角括号 */
            int corner = 10;

            context.batcher.box(x1, y1, x1 + corner, y1 + 1, 0xFF3A4050);
            context.batcher.box(x1, y1, x1 + 1, y1 + corner, 0xFF3A4050);
            context.batcher.box(x2 - corner, y1, x2, y1 + 1, 0xFF3A4050);
            context.batcher.box(x2 - 1, y1, x2, y1 + corner, 0xFF3A4050);
            context.batcher.box(x1, y2 - 1, x1 + corner, y2, 0xFF3A4050);
            context.batcher.box(x1, y2 - corner, x1 + 1, y2, 0xFF3A4050);
            context.batcher.box(x2 - corner, y2 - 1, x2, y2, 0xFF3A4050);
            context.batcher.box(x2 - 1, y2 - corner, x2, y2, 0xFF3A4050);

            /* 信息叠加层（半透明衬底保证亮背景下可读） */
            Film film = UIAIEditorPanel.this.getCurrentFilm();

            context.batcher.box(x1, y1 + 4, x2, y1 + 66, 0x90000000);
            context.batcher.text(L10n.lang("bbs_ai.editor.viewport.title").get(), x1 + 6, y1 + 8, 0xFFAFC4E8, true);

            if (film == null)
            {
                context.batcher.text(L10n.lang("bbs_ai.editor.viewport.no_film").get(), x1 + 6, y1 + 24, Colors.GRAY, true);
                context.batcher.text(L10n.lang("bbs_ai.editor.viewport.empty").get(), x1 + 6, y1 + 40, Colors.GRAY, true);
            }
            else
            {
                int clips = film.camera.getClips(mchorse.bbs_mod.utils.clips.Clip.class).size();

                context.batcher.text(L10n.lang("bbs_ai.editor.viewport.film").format(clips, film.replays.getList().size()).get(), x1 + 6, y1 + 24, 0xFF8FD694, true);

                if (this.selectedClip >= 0)
                {
                    context.batcher.text(L10n.lang("bbs_ai.editor.viewport.clip").format(this.selectedClip).get(), x1 + 6, y1 + 40, 0xFF6EC1FF, true);
                }

                mchorse.bbs_ai.preview.PreviewTrack track = PreviewSystem.get().getCache().get(STAGE_KEY);

                if (track != null && track.getFrameCount() > 0)
                {
                    context.batcher.text(L10n.lang("bbs_ai.editor.viewport.staged").format(track.getFrameCount(), track.getStartTick(), track.getEndTick()).get(), x1 + 6, y1 + 56, 0xFF8FD694, true);
                }
            }

            super.render(context);
        }
    }

    /**
     * Codex 风格配色与角色标头（对话区）
     */
    private static class ChatStyle
    {
        public static String header(Role role)
        {
            return L10n.lang(switch (role)
            {
                case USER -> "bbs_ai.editor.role.you";
                case ASSISTANT -> "bbs_ai.editor.role.ai";
                case ERROR -> "bbs_ai.editor.role.error";
                default -> "bbs_ai.editor.role.system";
            }).get();
        }

        public static int headerColor(Role role)
        {
            if (role == Role.USER)
            {
                return 0xFF6EA8E0;
            }

            if (role == Role.ASSISTANT)
            {
                return 0xFF7CC98B;
            }

            if (role == Role.ERROR)
            {
                return 0xFFE08080;
            }

            return 0xFF9AA3B2;
        }

        public static int bodyColor(Role role)
        {
            if (role == Role.USER)
            {
                return 0xFFC2D9F2;
            }

            if (role == Role.ASSISTANT)
            {
                return 0xFFCBE6D2;
            }

            if (role == Role.ERROR)
            {
                return 0xFFF2C2C2;
            }

            return 0xFFAAB4C4;
        }

        public static int accentColor(Role role)
        {
            if (role == Role.USER)
            {
                return 0xFF3A5A8F;
            }

            if (role == Role.ASSISTANT)
            {
                return 0xFF3F7A4C;
            }

            if (role == Role.ERROR)
            {
                return 0xFF8F3A3A;
            }

            return 0xFF3A4050;
        }
    }

    /**
     * 右栏底色（现代深色面板；面板本身透明以显示世界视口）
     */
    private static class ShadedColumn extends UIElement
    {
        @Override
        public void render(UIContext context)
        {
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xE80C0E15);
            context.batcher.box(this.area.x, this.area.y, this.area.x + 1, this.area.ey(), 0xFF2A2E3A);

            super.render(context);
        }
    }

    /* ====================================================================
     * 动作提取（协议解析）与可撤销动作
     * ==================================================================== */

    private List<Runnable> extractActions(Message message)
    {
        List<Runnable> actions = new ArrayList<>();

        if (message.role != Role.ASSISTANT)
        {
            return actions;
        }

        Matcher storyboard = STORYBOARD_BLOCK.matcher(message.text);

        while (storyboard.find())
        {
            actions.add(new StoryboardAction(storyboard.group(1).trim()));
        }

        Matcher motion = MOTION_BLOCK.matcher(message.text);

        while (motion.find())
        {
            actions.add(new MotionAction(motion.group(1).trim()));
        }

        return actions;
    }

    /**
     * storyboard 动作：解析 → 转换 → 写入 Films（登记撤销）
     */
    private class StoryboardAction implements Runnable
    {
        private final String json;

        public StoryboardAction(String json)
        {
            this.json = json;
        }

        @Override
        public void run()
        {
            AIToolCallLog.Entry call = UIAIEditorPanel.this.toolLog.begin("生成分镜 → 影片");

            try
            {
                StoryboardScript script = StoryboardScript.fromJson(this.json);
                Film current = UIAIEditorPanel.this.getCurrentFilm();

                if (current != null)
                {
                    /* 已打开影片：分镜合并进当前影片（快照 → 撤销可完整还原） */
                    MapType before = (MapType) current.toData();
                    int clipsBefore = current.camera.getClips(mchorse.bbs_mod.utils.clips.Clip.class).size();

                    new StoryboardToFilmConverter().convertInto(script, current);

                    AIUndoManager.get().record("film", L10n.lang("bbs_ai.editor.action.film.merge").get(),
                        () ->
                        {
                            Film panelFilm = UIAIEditorPanel.this.getCurrentFilm();

                            if (panelFilm != null)
                            {
                                panelFilm.fromData(before.copy());
                            }
                        },
                        () ->
                        {
                            Film panelFilm = UIAIEditorPanel.this.getCurrentFilm();

                            if (panelFilm != null)
                            {
                                panelFilm.fromData(before.copy());
                                new StoryboardToFilmConverter().convertInto(script, panelFilm);
                            }
                        });

                    UIAIEditorPanel.this.toolLog.complete(call, true, "已合并进当前影片（+" + (current.camera.getClips(mchorse.bbs_mod.utils.clips.Clip.class).size() - clipsBefore) + " 剪辑）");
                    UIAIEditorPanel.this.session.add(Role.SYSTEM, L10n.lang("bbs_ai.editor.action.film.merged").format(script.shots.size()).get());
                }
                else
                {
                    /* 未打开影片：新建（撤销 = 删除） */
                    Film film = new StoryboardToFilmConverter().convert(script);
                    MapType data = (MapType) film.toData();
                    String name = "ai_editor_" + System.currentTimeMillis() / 1000;

                    BBSMod.getFilms().create(name, data);

                    AIUndoManager.get().record("film", L10n.lang("bbs_ai.editor.action.film").get() + " " + name,
                        () -> BBSMod.getFilms().delete(name),
                        () -> BBSMod.getFilms().create(name, data));

                    UIAIEditorPanel.this.toolLog.complete(call, true, name + "（" + script.shots.size() + " 镜头）");
                    UIAIEditorPanel.this.session.add(Role.SYSTEM, L10n.lang("bbs_ai.editor.action.film.done").format(name, script.shots.size()).get());
                }

                UIAIEditorPanel.this.rebuildMessages();
            }
            catch (Exception e)
            {
                UIAIEditorPanel.this.toolLog.complete(call, false, e.getMessage());
                UIAIEditorPanel.this.session.add(Role.ERROR, L10n.lang("bbs_ai.editor.action.film.fail").get() + "：" + e.getMessage());
                UIAIEditorPanel.this.rebuildMessages();
            }
        }

        @Override
        public String toString()
        {
            return L10n.lang("bbs_ai.editor.action.film").get();
        }
    }

    /**
     * motion 动作：解析 → 进入预览系统（登记撤销；确认后烘焙）
     */
    private class MotionAction implements Runnable
    {
        private final String json;

        public MotionAction(String json)
        {
            this.json = json;
        }

        @Override
        public void run()
        {
            AIToolCallLog.Entry call = UIAIEditorPanel.this.toolLog.begin("应用动作 → 预览");

            try
            {
                MotionData data = MotionData.fromJson(this.json);
                List<MotionFrame> frames = new ArrayList<>(data.keyframes);

                PreviewSystem.get().stage("", "0", "", SourceType.INTERNAL_AI, frames);

                /* 撤销 = 丢弃暂存；重做 = 重新暂存 */
                AIUndoManager.get().record("motion", L10n.lang("bbs_ai.editor.action.motion").get() + "（" + frames.size() + " 帧）",
                    () -> PreviewSystem.get().getCache().discard(STAGE_KEY),
                    () -> PreviewSystem.get().stage("", "0", "", SourceType.INTERNAL_AI, frames));

                UIAIEditorPanel.this.toolLog.complete(call, true, frames.size() + " 帧已暂存");
                UIAIEditorPanel.this.session.add(Role.SYSTEM, L10n.lang("bbs_ai.editor.action.motion.done").format(frames.size()).get());
                UIAIEditorPanel.this.rebuildMessages();
            }
            catch (Exception e)
            {
                UIAIEditorPanel.this.toolLog.complete(call, false, e.getMessage());
                UIAIEditorPanel.this.session.add(Role.ERROR, L10n.lang("bbs_ai.editor.action.motion.fail").get() + "：" + e.getMessage());
                UIAIEditorPanel.this.rebuildMessages();
            }
        }

        @Override
        public String toString()
        {
            return L10n.lang("bbs_ai.editor.action.motion").get();
        }
    }
}
