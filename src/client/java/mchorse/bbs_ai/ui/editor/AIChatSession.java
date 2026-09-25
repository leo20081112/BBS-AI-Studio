package mchorse.bbs_ai.ui.editor;

import java.util.ArrayList;
import java.util.List;

import mchorse.bbs_ai.mods.AIContextService;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;

/**
 * AI 编辑器会话（客户端）
 *
 * <p>维护对话历史并组装提示词。系统提示词 = 角色设定 + mod 环境摘要
 * （{@link AIContextService}，AI 理解插件内容）+ 当前影片场景信息 + 动作协议
 * （AI 可输出 ```storyboard / ```motion JSON 代码块，编辑界面会转成可执行按钮）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class AIChatSession
{
    /**
     * 消息角色
     */
    public enum Role
    {
        USER, ASSISTANT, SYSTEM, ERROR
    }

    /**
     * 一条对话消息
     */
    public static class Message
    {
        public final Role role;
        public final String text;

        public Message(Role role, String text)
        {
            this.role = role;
            this.text = text;
        }
    }

    /**
     * 历史窗口：最多带入最近 N 轮（防止 token 爆炸）
     */
    private static final int HISTORY_TURNS = 8;

    /**
     * 对话历史
     */
    private final List<Message> history = new ArrayList<>();

    /**
     * 关联面板（读取当前影片信息）
     */
    private final UIDashboard dashboard;

    public AIChatSession(UIDashboard dashboard)
    {
        this.dashboard = dashboard;

        this.history.add(new Message(Role.SYSTEM, "AI 编辑器已就绪。输入你的想法，或点击下方快捷指令开始。"));
    }

    public List<Message> getHistory()
    {
        return this.history;
    }

    public void add(Role role, String text)
    {
        this.history.add(new Message(role, text));
    }

    /**
     * 清空历史（保留开场系统消息）
     */
    public void clear()
    {
        this.history.clear();

        this.history.add(new Message(Role.SYSTEM, "对话已清空。"));
    }

    /**
     * 组装系统提示词（角色 + 环境 + 场景 + 动作协议）
     */
    public String buildSystemPrompt()
    {
        StringBuilder builder = new StringBuilder();

        builder.append("你是 BBS AI Studio 的电影编辑助手，帮助用户在 Minecraft（BBS 拍摄框架）中创作影片。\n");
        builder.append("你可以：编写/修改分镜剧本、建议镜头语言与运镜、根据环境推荐演员（实体形态）与场景、解释 BBS 功能。\n\n");

        /* mod 环境摘要（自动识别 + 知识库 + 适配器说明） */
        try
        {
            builder.append(AIContextService.get().buildModDigest(false)).append("\n");
        }
        catch (Exception exception)
        {
            /* 环境摘要失败不阻塞对话 */
            System.err.println("[BBS AI] 构建环境摘要失败（忽略）：" + exception);
        }

        /* 当前影片场景信息 */
        String scene = this.buildSceneContext();

        if (!scene.isEmpty())
        {
            builder.append("\n## 当前编辑场景\n").append(scene);
        }

        /* 动作协议 */
        builder.append("\n## 动作协议（重要）\n");
        builder.append("当用户需要生成分镜剧本时，除了解说，必须在回复末尾输出一个 ```storyboard 代码块，\n");
        builder.append("内容是 JSON：{\"title\": \"影片名\", \"shots\": [{\"type\": \"camera\", ...}, {\"type\": \"actor\", ...}]}，\n");
        builder.append("字段与 BBS 分镜 DSL 一致（type=camera/actor/transition，含 position/point/duration 等）。\n");
        builder.append("用户界面会把该代码块渲染成「生成为影片」按钮，一键写入 Films。\n");
        builder.append("未要求生成时不要输出代码块。\n");

        return builder.toString();
    }

    /**
     * 组装本轮用户提示词（带最近历史窗口）
     */
    public String buildUserPrompt(String question)
    {
        StringBuilder builder = new StringBuilder();

        int from = Math.max(0, this.history.size() - HISTORY_TURNS * 2);

        for (int i = from; i < this.history.size(); i++)
        {
            Message message = this.history.get(i);

            if (message.role == Role.USER)
            {
                builder.append("用户：").append(message.text).append("\n");
            }
            else if (message.role == Role.ASSISTANT)
            {
                builder.append("助手：").append(message.text).append("\n");
            }
        }

        builder.append("用户：").append(question);

        return builder.toString();
    }

    /**
     * 当前影片场景信息（未打开影片面板则返回空串）
     */
    private String buildSceneContext()
    {
        try
        {
            UIFilmPanel panel = this.dashboard.getPanel(UIFilmPanel.class);

            if (panel == null)
            {
                return "";
            }

            Film film = panel.getData();

            if (film == null)
            {
                return "";
            }

            StringBuilder builder = new StringBuilder();

            builder.append("- 已打开影片，角色（Replay）数量：").append(film.replays.getList().size()).append("\n");

            String description = film.description == null ? "" : film.description.get();

            if (description != null && !description.isEmpty())
            {
                builder.append("- 影片描述：").append(description.length() > 120 ? description.substring(0, 120) + "…" : description).append("\n");
            }

            builder.append("- 相机剪辑数量：").append(film.camera.getClips(mchorse.bbs_mod.utils.clips.Clip.class).size()).append("\n");

            return builder.toString();
        }
        catch (Exception exception)
        {
            return "";
        }
    }
}
