package mchorse.bbs_ai.storyboard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_ai.core.api.APIResponseParser;

import java.util.ArrayList;
import java.util.List;

/**
 * 分镜脚本根对象
 *
 * <p>由大模型输出的严格 JSON 解析而来，包含有序的 {@link Shot} 列表。
 * 解析过程容错：剥离 Markdown 代码围栏、跳过未知 / 缺字段的 Shot。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class StoryboardScript
{
    /**
     * 有序分镜列表
     */
    public List<Shot> shots = new ArrayList<>();

    /**
     * 脚本标题（可选，由模型生成或用户输入）
     */
    public String title = "";

    /**
     * 计算整部影片的总时长（tick）
     */
    public int getTotalDuration()
    {
        int duration = 0;

        for (Shot shot : this.shots)
        {
            if (shot instanceof CameraShot)
            {
                duration += ((CameraShot) shot).durationTicks;
            }
            else if (shot instanceof ActorShot)
            {
                duration += ((ActorShot) shot).durationTicks;
            }
            else if (shot instanceof TransitionShot)
            {
                /* 转场与相邻镜头重叠，不计入总时长 */
            }
        }

        return Math.max(duration, 20);
    }

    /**
     * 序列化为 JSON（用于展示与调试）
     */
    public String toJsonString()
    {
        JsonObject root = new JsonObject();

        root.addProperty("title", this.title);

        com.google.gson.JsonArray array = new com.google.gson.JsonArray();

        for (Shot shot : this.shots)
        {
            array.add(shot.toJson());
        }

        root.add("shots", array);

        return new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
    }

    /**
     * 从模型原始输出解析分镜脚本
     *
     * @param raw 模型原始文本（可含代码围栏与说明文字）
     * @return 解析后的脚本
     * @throws IllegalArgumentException JSON 无效或不含 shots 数组
     */
    public static StoryboardScript fromJson(String raw)
    {
        String json = APIResponseParser.extractJsonBlock(raw);
        JsonElement element = JsonParser.parseString(json);

        if (!element.isJsonObject())
        {
            throw new IllegalArgumentException("分镜 JSON 根节点不是对象");
        }

        JsonObject root = element.getAsJsonObject();

        if (!root.has("shots") || !root.get("shots").isJsonArray())
        {
            throw new IllegalArgumentException("分镜 JSON 缺少 shots 数组");
        }

        StoryboardScript script = new StoryboardScript();

        script.title = root.has("title") && root.get("title").isJsonPrimitive() ? root.get("title").getAsString() : "";

        for (JsonElement shotElement : root.getAsJsonArray("shots"))
        {
            if (!shotElement.isJsonObject())
            {
                continue;
            }

            JsonObject shotObject = shotElement.getAsJsonObject();
            String type = shotObject.has("type") && shotObject.get("type").isJsonPrimitive()
                ? shotObject.get("type").getAsString()
                : "";

            try
            {
                switch (type)
                {
                    case "camera": script.shots.add(CameraShot.fromJson(shotObject)); break;
                    case "actor": script.shots.add(ActorShot.fromJson(shotObject)); break;
                    case "transition": script.shots.add(TransitionShot.fromJson(shotObject)); break;
                    default: System.err.println("[BBS AI] 跳过未知分镜类型：" + type); break;
                }
            }
            catch (Exception e)
            {
                System.err.println("[BBS AI] 解析分镜单元失败（已跳过）：" + e.getMessage());
            }
        }

        if (script.shots.isEmpty())
        {
            throw new IllegalArgumentException("分镜脚本中没有任何有效镜头");
        }

        return script;
    }
}
