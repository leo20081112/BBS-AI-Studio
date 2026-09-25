package mchorse.bbs_ai.format;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * 动作数据的元信息与角色描述
 *
 * <p>对应统一导出格式 {@code bbs_ai_studio_motion_v1} 中的
 * {@code metadata} 与 {@code actor} 节点。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class MotionMetadata
{
    /**
     * 数据来源：video / text / ik_manual / external_tool
     */
    public String source = "video";

    /**
     * 原始文件名
     */
    public String sourceFile = "";

    /**
     * 原始视频帧率
     */
    public int fps = 30;

    /**
     * 总帧数
     */
    public int totalFrames = 0;

    /**
     * 使用的 AI 模型（mediapipe_pose / yolov8 / rtmpose / gpt4v / claude ...）
     */
    public String model = "";

    /**
     * ISO 8601 生成时间戳
     */
    public String generatedAt = "";

    /**
     * 生成工具版本
     */
    public String toolVersion = "1.0.0";

    /**
     * 生成工具标识（bbs_ai_studio_ingame / bbs_ai_toolchain）
     */
    public String generator = "bbs_ai_studio_ingame";

    /**
     * 目标 Form 类型
     */
    public String targetForm = "minecraft:player";

    /**
     * 骨骼类型
     */
    public String skeletonType = "standard_6bone";

    /**
     * 序列化为 JSON（metadata + actor 两节点）
     */
    public void toJson(JsonObject root)
    {
        JsonObject metadata = new JsonObject();

        metadata.addProperty("source", this.source);
        metadata.addProperty("source_file", this.sourceFile);
        metadata.addProperty("fps", this.fps);
        metadata.addProperty("total_frames", this.totalFrames);
        metadata.addProperty("model", this.model);
        metadata.addProperty("generated_at", this.generatedAt);
        metadata.addProperty("tool_version", this.toolVersion);
        metadata.addProperty("generator", this.generator);

        JsonObject actor = new JsonObject();

        actor.addProperty("target_form", this.targetForm);
        actor.addProperty("skeleton_type", this.skeletonType);

        root.add("metadata", metadata);
        root.add("actor", actor);
    }

    /**
     * 从 JSON 反序列化
     */
    public static MotionMetadata fromJson(JsonObject root)
    {
        MotionMetadata meta = new MotionMetadata();

        if (root.has("metadata") && root.get("metadata").isJsonObject())
        {
            JsonObject metadata = root.getAsJsonObject("metadata");

            meta.source = getString(metadata, "source", meta.source);
            meta.sourceFile = getString(metadata, "source_file", meta.sourceFile);
            meta.fps = getInt(metadata, "fps", meta.fps);
            meta.totalFrames = getInt(metadata, "total_frames", meta.totalFrames);
            meta.model = getString(metadata, "model", meta.model);
            meta.generatedAt = getString(metadata, "generated_at", meta.generatedAt);
            meta.toolVersion = getString(metadata, "tool_version", meta.toolVersion);
            meta.generator = getString(metadata, "generator", meta.generator);
        }

        if (root.has("actor") && root.get("actor").isJsonObject())
        {
            JsonObject actor = root.getAsJsonObject("actor");

            meta.targetForm = getString(actor, "target_form", meta.targetForm);
            meta.skeletonType = getString(actor, "skeleton_type", meta.skeletonType);
        }

        return meta;
    }

    /**
     * 安全读取字符串字段
     */
    static String getString(JsonObject object, String key, String defaultValue)
    {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : defaultValue;
    }

    /**
     * 安全读取整数字段
     */
    static int getInt(JsonObject object, String key, int defaultValue)
    {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsInt() : defaultValue;
    }

    /**
     * 安全读取浮点字段
     */
    static float getFloat(JsonObject object, String key, float defaultValue)
    {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsFloat() : defaultValue;
    }

    /**
     * 安全读取三维向量字段
     */
    static float[] getVector(JsonObject object, String key)
    {
        if (!object.has(key) || !object.get(key).isJsonArray())
        {
            return null;
        }

        JsonArray array = object.getAsJsonArray(key);

        if (array.size() < 3)
        {
            return null;
        }

        return new float[] {array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    /**
     * 写入三维向量字段
     */
    static JsonArray putVector(float x, float y, float z)
    {
        JsonArray array = new JsonArray();

        array.add(x);
        array.add(y);
        array.add(z);

        return array;
    }
}
