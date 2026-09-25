package mchorse.bbs_ai.format;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 动作数据帧中单根骨骼的姿态
 *
 * <p>对应统一导出格式 {@code bbs_ai_studio_motion_v1} 中
 * {@code keyframes[].bones.<bone>} 节点：欧拉旋转角 [x, y, z]（度）与插值类型。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BonePose
{
    /**
     * 欧拉旋转角 [x, y, z]，单位：度
     */
    public float[] rotation = new float[] {0.0F, 0.0F, 0.0F};

    /**
     * 插值类型（linear / smooth 等）
     */
    public String interpolation = "linear";

    public BonePose()
    {}

    public BonePose(float x, float y, float z)
    {
        this.rotation = new float[] {x, y, z};
    }

    /**
     * 序列化为 JSON 对象
     */
    public JsonObject toJson()
    {
        JsonObject object = new JsonObject();
        JsonArray array = new JsonArray();

        array.add(this.rotation[0]);
        array.add(this.rotation[1]);
        array.add(this.rotation[2]);

        object.add("rotation", array);
        object.addProperty("interpolation", this.interpolation);

        return object;
    }

    /**
     * 从 JSON 元素反序列化（结构不合法时保留默认值）
     */
    public static BonePose fromJson(JsonElement element)
    {
        BonePose pose = new BonePose();

        if (element == null || !element.isJsonObject())
        {
            return pose;
        }

        JsonObject object = element.getAsJsonObject();

        if (object.has("rotation") && object.get("rotation").isJsonArray())
        {
            JsonArray array = object.getAsJsonArray("rotation");

            for (int i = 0; i < 3 && i < array.size(); i++)
            {
                pose.rotation[i] = array.get(i).getAsFloat();
            }
        }

        if (object.has("interpolation") && object.get("interpolation").isJsonPrimitive())
        {
            pose.interpolation = object.get("interpolation").getAsString();
        }

        return pose;
    }
}
