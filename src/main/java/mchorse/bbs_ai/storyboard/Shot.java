package mchorse.bbs_ai.storyboard;

import com.google.gson.JsonObject;

/**
 * 分镜 Shot 基类
 *
 * <p>分镜脚本的原子单元，子类包括 {@link CameraShot}（镜头运动）、
 * {@link ActorShot}（角色动作）与 {@link TransitionShot}（转场）。
 * 由大模型按 JSON Schema 输出，经 {@link StoryboardScript#fromJson(String)} 解析。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public abstract class Shot
{
    /**
     * Shot 类型标识（camera / actor / transition）
     */
    public abstract String getType();

    /**
     * 序列化为 JSON 对象（子类先调用 super 填充 type）
     */
    public JsonObject toJson()
    {
        JsonObject object = new JsonObject();

        object.addProperty("type", this.getType());

        return object;
    }
}
