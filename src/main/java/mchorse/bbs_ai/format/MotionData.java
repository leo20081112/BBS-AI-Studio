package mchorse.bbs_ai.format;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 统一导出格式 {@code bbs_ai_studio_motion_v1} 根对象
 *
 * <p>这是游戏内 AI 生成、IK 调整与外部 Python 工具链共同遵循的数据契约。
 * 序列化使用 Gson 输出严格 JSON，保证跨工具互通。</p>
 *
 * <p>JSON 结构见项目总提示词「六、统一导出格式契约」。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class MotionData
{
    /**
     * 格式标识（固定值）
     */
    public static final String FORMAT = "bbs_ai_studio_motion_v1";

    /**
     * 元信息
     */
    public MotionMetadata metadata = new MotionMetadata();

    /**
     * 关键帧列表（按 tick 升序）
     */
    public List<MotionFrame> keyframes = new ArrayList<>();

    /**
     * 镜头运动数据（可选，可空）
     */
    public List<JsonObject> cameraShots = new ArrayList<>();

    /**
     * 动作剪辑数据（可选，可空）
     */
    public List<JsonObject> actions = new ArrayList<>();

    /**
     * 填充默认元信息时间戳
     */
    public void stampGenerated(String source, String sourceFile, int fps, int totalFrames, String model, String generator)
    {
        this.metadata.source = source;
        this.metadata.sourceFile = sourceFile == null ? "" : sourceFile;
        this.metadata.fps = fps;
        this.metadata.totalFrames = totalFrames;
        this.metadata.model = model;
        this.metadata.generator = generator;
        this.metadata.generatedAt = Instant.now().toString();
    }

    /**
     * 添加关键帧并保持按 tick 排序
     */
    public void addFrame(MotionFrame frame)
    {
        this.keyframes.add(frame);
        this.keyframes.sort(Comparator.comparingInt(f -> f.tick));
    }

    /**
     * 序列化为 JSON 对象
     */
    public JsonObject toJson()
    {
        JsonObject root = new JsonObject();

        root.addProperty("format", FORMAT);
        this.metadata.toJson(root);

        JsonArray keyframes = new JsonArray();

        for (MotionFrame frame : this.keyframes)
        {
            keyframes.add(frame.toJson());
        }

        root.add("keyframes", keyframes);

        JsonArray cameraShots = new JsonArray();

        for (JsonObject shot : this.cameraShots)
        {
            cameraShots.add(shot);
        }

        root.add("camera_shots", cameraShots);

        JsonArray actions = new JsonArray();

        for (JsonObject action : this.actions)
        {
            actions.add(action);
        }

        root.add("actions", actions);

        return root;
    }

    /**
     * 序列化为格式化 JSON 字符串
     */
    public String toJsonString()
    {
        return new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(this.toJson());
    }

    /**
     * 从 JSON 文本反序列化（格式标识不匹配时仍尝试解析，仅告警）
     */
    public static MotionData fromJson(String json)
    {
        MotionData data = new MotionData();

        if (json == null || json.trim().isEmpty())
        {
            throw new IllegalArgumentException("动作数据 JSON 为空");
        }

        JsonElement element = JsonParser.parseString(json);

        if (!element.isJsonObject())
        {
            throw new IllegalArgumentException("动作数据 JSON 根节点不是对象");
        }

        JsonObject root = element.getAsJsonObject();
        String format = root.has("format") ? root.get("format").getAsString() : "";

        if (!FORMAT.equals(format))
        {
            System.err.println("[BBS AI] 动作数据格式标识异常：\"" + format + "\"，仍将尝试解析");
        }

        data.metadata = MotionMetadata.fromJson(root);

        if (root.has("keyframes") && root.get("keyframes").isJsonArray())
        {
            for (JsonElement frameElement : root.getAsJsonArray("keyframes"))
            {
                data.keyframes.add(MotionFrame.fromJson(frameElement));
            }
        }

        data.cameraShots = readObjectArray(root, "camera_shots");
        data.actions = readObjectArray(root, "actions");

        data.keyframes.sort(Comparator.comparingInt(f -> f.tick));

        return data;
    }

    /**
     * 读取 JSON 对象数组字段（缺失或类型不符时返回空列表）
     */
    private static List<JsonObject> readObjectArray(JsonObject root, String key)
    {
        List<JsonObject> list = new ArrayList<>();

        if (root.has(key) && root.get(key).isJsonArray())
        {
            for (JsonElement element : root.getAsJsonArray(key))
            {
                if (element.isJsonObject())
                {
                    list.add(element.getAsJsonObject());
                }
            }
        }

        return list;
    }

    /**
     * 写入文件（格式化 JSON）
     *
     * @return 是否写入成功
     */
    public boolean writeToFile(File file)
    {
        try
        {
            file.getParentFile().mkdirs();
            IOUtils.writeText(file, this.toJsonString());

            return true;
        }
        catch (IOException e)
        {
            System.err.println("[BBS AI] 写入动作数据失败：" + file + "，" + e.getMessage());

            return false;
        }
    }

    /**
     * 从文件读取
     *
     * @throws IOException 文件读取失败
     */
    public static MotionData readFromFile(File file) throws IOException
    {
        return fromJson(IOUtils.readText(file));
    }
}
