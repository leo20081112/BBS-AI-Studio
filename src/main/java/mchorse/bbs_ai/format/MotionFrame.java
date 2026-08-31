package mchorse.bbs_ai.format;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单个关键帧：时间轴刻度 + 各骨骼旋转数据
 *
 * <p>对应统一导出格式 {@code bbs_ai_studio_motion_v1} 中的
 * {@code keyframes[]} 元素。时间刻度以 Minecraft tick 为单位（20 ticks = 1 秒）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class MotionFrame
{
    /**
     * 时间轴刻度（20 ticks = 1 秒）
     */
    public int tick;

    /**
     * 骨骼名称 → 旋转数据（使用 LinkedHashMap 保持写入顺序）
     */
    public Map<String, BonePose> bones = new LinkedHashMap<>();

    public MotionFrame()
    {}

    public MotionFrame(int tick)
    {
        this.tick = tick;
    }

    /**
     * 设置某骨骼姿态（不存在时创建）
     */
    public MotionFrame bone(String name, float x, float y, float z)
    {
        this.bones.put(name, new BonePose(x, y, z));

        return this;
    }

    /**
     * 序列化为 JSON 对象
     */
    public JsonObject toJson()
    {
        JsonObject object = new JsonObject();

        object.addProperty("tick", this.tick);

        JsonObject bones = new JsonObject();

        for (Map.Entry<String, BonePose> entry : this.bones.entrySet())
        {
            bones.add(entry.getKey(), entry.getValue().toJson());
        }

        object.add("bones", bones);

        return object;
    }

    /**
     * 从 JSON 元素反序列化
     */
    public static MotionFrame fromJson(JsonElement element)
    {
        MotionFrame frame = new MotionFrame();

        if (element == null || !element.isJsonObject())
        {
            return frame;
        }

        JsonObject object = element.getAsJsonObject();

        frame.tick = object.has("tick") ? object.get("tick").getAsInt() : 0;

        if (object.has("bones") && object.get("bones").isJsonObject())
        {
            for (Map.Entry<String, JsonElement> entry : object.getAsJsonObject("bones").entrySet())
            {
                frame.bones.put(entry.getKey(), BonePose.fromJson(entry.getValue()));
            }
        }

        return frame;
    }
}
