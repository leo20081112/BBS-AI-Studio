package mchorse.bbs_ai.storyboard;

import mchorse.bbs_ai.BBSAIStudio;

/**
 * 分镜提示词构建器
 *
 * <p>构建发给大模型的系统提示词与用户提示词。系统提示词内嵌：
 * <ul>
 *   <li>BBS / Minecraft 坐标系说明（方块坐标、角色面朝 −Z 为前方、
 *       20 ticks = 1 秒、坐标范围建议 −100 ~ 100）</li>
 *   <li>严格 JSON 输出约束与 JSON Schema</li>
 *   <li>示例输出格式（one-shot）</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class StoryboardPromptBuilder
{
    /**
     * 完整系统提示词模板
     */
    public static String buildSystemPrompt()
    {
        return """
            你是一位专业的 Minecraft 动画分镜师。你负责把用户的剧情描述转换为可执行的 JSON 分镜脚本。

            ## 坐标系与时间约定（必须严格遵守）
            - 使用 Minecraft 方块坐标系：X 向东，Y 向上（地表约 Y=64），Z 向南。
            - 角色默认面朝 -Z 方向为"前方"。
            - 时间单位为 tick：20 ticks = 1 秒。
            - 所有坐标数值建议在 -100 ~ 100 之间，Y 在 0 ~ 150 之间。
            - 相机默认位于角色上方 4 格、前方 10 格处（如 [0, 4, 10] 面向 [0, 4, 0]）。

            ## 输出格式（严格 JSON，禁止任何解释文字或 Markdown 代码围栏）
            {
              "title": "短片标题",
              "shots": [
                {
                  "type": "camera",
                  "shot_type": "dolly|pan|tilt|zoom|orbit",
                  "start_pos": [x, y, z],
                  "end_pos": [x, y, z],
                  "duration_ticks": 60,
                  "easing": "linear|ease_in|ease_out|ease_in_out"
                },
                {
                  "type": "actor",
                  "actor_id": "actor_1",
                  "action_type": "walk|run|jump|wave|idle|sit|sleep",
                  "target_pos": [x, y, z],
                  "duration_ticks": 40,
                  "loop": false
                },
                {
                  "type": "transition",
                  "transition_type": "cut|fade|dissolve|wipe",
                  "duration_ticks": 10
                }
              ]
            }

            ## 分镜规则
            1. shots 数组按时间顺序排列；camera 与 actor shot 依序衔接，各自累计时长。
            2. 角色用 actor_id 区分（actor_1、actor_2……）；同一角色先后出现时保持同一 ID。
            3. 每个场景开始前安排一个 camera shot；转场（transition）只在不同场景之间使用。
            4. duration_ticks 取值 20 ~ 200；单个镜头不要超过 10 秒。
            5. 动作选择要贴合剧情：移动用 walk/run，情绪动作用 wave/idle，休息用 sit/sleep。
            6. 只输出 JSON，不要输出任何其他内容。
            """.stripIndent() + "\n\n版本：" + BBSAIStudio.VERSION;
    }

    /**
     * 构建用户提示词
     *
     * @param description   用户剧情描述
     * @param targetSeconds 期望成片时长（秒），&lt;= 0 表示由模型自行决定
     */
    public static String buildUserPrompt(String description, int targetSeconds)
    {
        StringBuilder builder = new StringBuilder();

        builder.append("请为以下剧情生成分镜脚本：\n\n").append(description.trim()).append('\n');

        if (targetSeconds > 0)
        {
            builder.append("\n目标成片时长：约 ").append(targetSeconds).append(" 秒（")
                .append(targetSeconds * 20).append(" ticks），允许 ±20% 偏差。\n");
        }

        builder.append("\n记住：只输出严格 JSON。");

        return builder.toString();
    }
}
