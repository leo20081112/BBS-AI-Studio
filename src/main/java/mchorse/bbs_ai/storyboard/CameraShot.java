package mchorse.bbs_ai.storyboard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 镜头 Shot：相机运动描述
 *
 * <p>支持运动类型：dolly（推拉）、pan（水平摇）、tilt（俯仰摇）、
 * zoom（变焦）、orbit（环绕）。位置采用 Minecraft 方块坐标，
 * 时间以 tick 计（20 ticks = 1 秒）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class CameraShot extends Shot
{
    /**
     * 运动类型常量
     */
    public static final String DOLLY = "dolly";
    public static final String PAN = "pan";
    public static final String TILT = "tilt";
    public static final String ZOOM = "zoom";
    public static final String ORBIT = "orbit";

    /**
     * 镜头运动类型
     */
    public String shotType = DOLLY;

    /**
     * 起始位置 [x, y, z]
     */
    public float[] startPos = new float[] {0.0F, 4.0F, 10.0F};

    /**
     * 结束位置 [x, y, z]（pan/tilt/zoom 时作为参考朝向/焦距终点）
     */
    public float[] endPos = new float[] {0.0F, 4.0F, 0.0F};

    /**
     * 起始朝向（yaw，度，可空 = 自动面向 endPos）
     */
    public Float startYaw;

    /**
     * 起始俯仰（pitch，度，可空）
     */
    public Float startPitch;

    /**
     * 持续时间（tick，20 ticks = 1 秒）
     */
    public int durationTicks = 60;

    /**
     * 缓动曲线（linear / ease_in / ease_out / ease_in_out）
     */
    public String easing = "ease_in_out";

    @Override
    public String getType()
    {
        return "camera";
    }

    @Override
    public JsonObject toJson()
    {
        JsonObject object = super.toJson();

        object.addProperty("shot_type", this.shotType);
        object.add("start_pos", vector(this.startPos));
        object.add("end_pos", vector(this.endPos));

        if (this.startYaw != null)
        {
            object.addProperty("start_yaw", this.startYaw);
        }

        if (this.startPitch != null)
        {
            object.addProperty("start_pitch", this.startPitch);
        }

        object.addProperty("duration_ticks", this.durationTicks);
        object.addProperty("easing", this.easing);

        return object;
    }

    /**
     * 从模型输出的 JSON 解析
     */
    public static CameraShot fromJson(JsonObject object)
    {
        CameraShot shot = new CameraShot();

        shot.shotType = getString(object, "shot_type", DOLLY);
        shot.startPos = getVector(object, "start_pos", shot.startPos);
        shot.endPos = getVector(object, "end_pos", shot.endPos);
        shot.durationTicks = Math.max(1, getInt(object, "duration_ticks", 60));
        shot.easing = getString(object, "easing", shot.easing);

        if (object.has("start_yaw") && object.get("start_yaw").isJsonPrimitive())
        {
            shot.startYaw = object.get("start_yaw").getAsFloat();
        }

        if (object.has("start_pitch") && object.get("start_pitch").isJsonPrimitive())
        {
            shot.startPitch = object.get("start_pitch").getAsFloat();
        }

        return shot;
    }

    /**
     * 写入三维向量
     */
    static JsonElement vector(float[] v)
    {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();

        array.add(v[0]);
        array.add(v[1]);
        array.add(v[2]);

        return array;
    }

    /**
     * 读取字符串字段
     */
    static String getString(JsonObject object, String key, String defaultValue)
    {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : defaultValue;
    }

    /**
     * 读取整数字段
     */
    static int getInt(JsonObject object, String key, int defaultValue)
    {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : defaultValue;
    }

    /**
     * 读取布尔字段
     */
    static boolean getBool(JsonObject object, String key, boolean defaultValue)
    {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsBoolean() : defaultValue;
    }

    /**
     * 读取三维向量字段
     */
    static float[] getVector(JsonObject object, String key, float[] defaultValue)
    {
        if (!object.has(key) || !object.get(key).isJsonArray())
        {
            return defaultValue;
        }

        com.google.gson.JsonArray array = object.getAsJsonArray(key);

        if (array.size() < 3)
        {
            return defaultValue;
        }

        return new float[] {array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }
}
