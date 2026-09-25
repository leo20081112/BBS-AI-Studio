package mchorse.bbs_ai.model;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * BBS AI Studio 统一人物模型格式 {@code .bbsm}（GZIP + JSON，单文件）
 *
 * <p>单文件封装一个人物模型的全部数据（后续要求 1「导出时单文件导出和导入」）：
 * <ul>
 *   <li>{@code metadata} —— 名称 / 作者 / 描述 / 版本 / 来源 / 标签</li>
 *   <li>{@code model_id} —— 原 {@code ModelForm.model} 模型 ID</li>
 *   <li>{@code form} —— {@code FormUtils.toData(ModelForm)} 全量序列化（姿态、IK、物理、动作等）</li>
 *   <li>{@code model_files} —— 原始模型目录内嵌文件（model.bobj / config.json / shape_keys.json），
 *       保证游戏内往返导入零损失</li>
 *   <li>{@code textures} —— 内嵌纹理（主纹理 + 材质纹理，Base64），路径相对 {@code models/<id>/}</li>
 *   <li>{@code geometry} —— 解析后的几何（顶点 / 权重 / UV / 法线 / 三角面），供外部工具直读</li>
 *   <li>{@code skeleton} —— 骨骼层级与绑定矩阵（列主序 4x4）</li>
 * </ul>
 * 外部工具（Blender 插件）生成的 .bbsm 可只含 {@code geometry}+{@code skeleton}+{@code textures}，
 * 导入器会用 {@link BOBJWriter} 现场生成 model.bobj。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BBSSModel
{
    /**
     * 格式标识（固定值）
     */
    public static final String FORMAT = "bbs_ai_studio_model_v1";

    /**
     * 格式版本号（写在 metadata.version）
     */
    public static final String FORMAT_VERSION = "1.0";

    /**
     * 元数据：名称
     */
    public String name = "";

    /**
     * 元数据：作者
     */
    public String author = "";

    /**
     * 元数据：描述
     */
    public String description = "";

    /**
     * 元数据：来源工具（bbs_ai_studio / blender / blockbench …）
     */
    public String source = "bbs_ai_studio";

    /**
     * 元数据：创建时间 ISO 8601
     */
    public String createdAt = "";

    /**
     * 元数据：标签
     */
    public List<String> tags = new ArrayList<>();

    /**
     * 原 ModelForm.model 模型 ID（几何数据所在 assets/models/&lt;id&gt;/）
     */
    public String modelId = "";

    /**
     * 完整 ModelForm 序列化（DataJson 转换的 JSON 对象；可为 null 表示外部生成）
     */
    public JsonObject form;

    /**
     * 内嵌原始模型文件：相对 models/&lt;id&gt;/ 的路径 → 文本内容
     * （model.bobj / config.json / shape_keys.json 均为文本文件）
     */
    public Map<String, String> modelFiles = new LinkedHashMap<>();

    /**
     * 内嵌纹理列表
     */
    public List<TextureEntry> textures = new ArrayList<>();

    /**
     * 几何数据（可为 null：无 .bobj 可解析时）
     */
    public JsonObject geometry;

    /**
     * 骨骼数据（可为 null）
     */
    public JsonObject skeleton;

    /**
     * 姿态（Pose.toData() 的 JSON 形态；可为 null）
     */
    public JsonObject pose;

    /**
     * 形态键权重（ShapeKeys.toData()；可为 null）
     */
    public JsonObject shapeKeys;

    /**
     * IK 约束（ModelForm.ik 的 MapType；可为 null）
     */
    public JsonObject ik;

    /**
     * 物理配置（ModelForm.physics 的 MapType；可为 null）
     */
    public JsonObject physics;

    /**
     * 动作配置（ActionsConfig.toData()；可为 null）
     */
    public JsonObject actions;

    /**
     * 骨骼映射（标准六骨骼槽位 → 模型骨骼名；{@code {"head":"head","left_arm":"arm_l",...}}）
     *
     * <p>导出时从表单挂载值读取，导入时写回表单，供视频识别烘焙写入正确的骨骼
     * （见 {@link mchorse.bbs_ai.motion.SkeletonMapping}）；可为 null。</p>
     */
    public JsonObject skeletonMapping;

    /**
     * 内嵌纹理条目
     *
     * @param role 纹理角色：{@code main} 或 {@code material:<材质名>}
     * @param path 原资源路径（assets 内相对路径，如 {@code models/foo/model.png}）
     */
    public static class TextureEntry
    {
        public String role;
        public String path;
        public String encoding = "base64";
        public String content;
        public int size;

        public TextureEntry(String role, String path, byte[] bytes)
        {
            this.role = role;
            this.path = path;
            this.content = Base64.getEncoder().encodeToString(bytes);
            this.size = bytes.length;
        }

        public TextureEntry()
        {}

        public byte[] decode()
        {
            return this.content == null || this.content.isEmpty()
                ? new byte[0]
                : Base64.getDecoder().decode(this.content);
        }
    }

    /**
     * 填充默认元数据时间戳
     */
    public void stampGenerated(String name, String author, String description, String source)
    {
        if (name != null && !name.isEmpty())
        {
            this.name = name;
        }

        this.author = author == null ? "" : author;
        this.description = description == null ? "" : description;
        this.source = source == null ? "bbs_ai_studio" : source;
        this.createdAt = Instant.now().toString();
    }

    /**
     * 序列化为 JSON 对象
     */
    public JsonObject toJson()
    {
        JsonObject root = new JsonObject();

        root.addProperty("format", FORMAT);

        JsonObject metadata = new JsonObject();

        metadata.addProperty("name", this.name);
        metadata.addProperty("author", this.author);
        metadata.addProperty("description", this.description);
        metadata.addProperty("version", FORMAT_VERSION);
        metadata.addProperty("created_at", this.createdAt);
        metadata.addProperty("source", this.source);

        JsonArray tags = new JsonArray();

        for (String tag : this.tags)
        {
            tags.add(tag);
        }

        metadata.add("tags", tags);
        root.add("metadata", metadata);

        root.addProperty("model_id", this.modelId);

        if (this.form != null)
        {
            root.add("form", this.form);
        }

        if (!this.modelFiles.isEmpty())
        {
            JsonObject files = new JsonObject();

            for (Map.Entry<String, String> entry : this.modelFiles.entrySet())
            {
                JsonObject file = new JsonObject();

                file.addProperty("encoding", "text");
                file.addProperty("content", entry.getValue());
                files.add(entry.getKey(), file);
            }

            root.add("model_files", files);
        }

        if (!this.textures.isEmpty())
        {
            JsonArray textures = new JsonArray();

            for (TextureEntry texture : this.textures)
            {
                JsonObject entry = new JsonObject();

                entry.addProperty("role", texture.role);
                entry.addProperty("path", texture.path);
                entry.addProperty("encoding", texture.encoding);
                entry.addProperty("content", texture.content);
                entry.addProperty("size", texture.size);
                textures.add(entry);
            }

            root.add("textures", textures);
        }

        if (this.geometry != null)
        {
            root.add("geometry", this.geometry);
        }

        if (this.skeleton != null)
        {
            root.add("skeleton", this.skeleton);
        }

        if (this.pose != null)
        {
            root.add("pose", this.pose);
        }

        if (this.shapeKeys != null)
        {
            root.add("shape_keys", this.shapeKeys);
        }

        if (this.ik != null)
        {
            root.add("ik", this.ik);
        }

        if (this.physics != null)
        {
            root.add("physics", this.physics);
        }

        if (this.actions != null)
        {
            root.add("actions", this.actions);
        }

        if (this.skeletonMapping != null)
        {
            root.add("skeleton_mapping", this.skeletonMapping);
        }

        return root;
    }

    /**
     * 序列化为格式化 JSON 字符串（调试 / 外部工具直读用；落盘走 GZIP）
     */
    public String toJsonString()
    {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(this.toJson());
    }

    /**
     * 从 JSON 对象反序列化
     */
    public static BBSSModel fromJson(JsonObject root)
    {
        BBSSModel model = new BBSSModel();

        if (root == null)
        {
            throw new IllegalArgumentException(".bbsm 根节点不是对象");
        }

        String format = getString(root, "format", "");

        if (!FORMAT.equals(format))
        {
            System.err.println("[BBS AI] 模型格式标识异常：\"" + format + "\"，仍将尝试解析");
        }

        JsonObject metadata = root.has("metadata") && root.get("metadata").isJsonObject()
            ? root.getAsJsonObject("metadata")
            : new JsonObject();

        model.name = getString(metadata, "name", "");
        model.author = getString(metadata, "author", "");
        model.description = getString(metadata, "description", "");
        model.source = getString(metadata, "source", "");
        model.createdAt = getString(metadata, "created_at", "");

        if (metadata.has("tags") && metadata.get("tags").isJsonArray())
        {
            for (JsonElement tag : metadata.getAsJsonArray("tags"))
            {
                model.tags.add(tag.getAsString());
            }
        }

        model.modelId = getString(root, "model_id", "");

        if (root.has("form") && root.get("form").isJsonObject())
        {
            model.form = root.getAsJsonObject("form");
        }

        if (root.has("model_files") && root.get("model_files").isJsonObject())
        {
            JsonObject files = root.getAsJsonObject("model_files");

            for (Map.Entry<String, JsonElement> entry : files.entrySet())
            {
                if (entry.getValue().isJsonObject())
                {
                    JsonObject file = entry.getValue().getAsJsonObject();

                    model.modelFiles.put(entry.getKey(), getString(file, "content", ""));
                }
            }
        }

        if (root.has("textures") && root.get("textures").isJsonArray())
        {
            for (JsonElement element : root.getAsJsonArray("textures"))
            {
                if (!element.isJsonObject())
                {
                    continue;
                }

                JsonObject entry = element.getAsJsonObject();
                TextureEntry texture = new TextureEntry();

                texture.role = getString(entry, "role", "main");
                texture.path = getString(entry, "path", "");
                texture.encoding = getString(entry, "encoding", "base64");
                texture.content = getString(entry, "content", "");
                texture.size = entry.has("size") && entry.get("size").isJsonPrimitive() ? entry.get("size").getAsInt() : 0;
                model.textures.add(texture);
            }
        }

        model.geometry = getOptionalObject(root, "geometry");
        model.skeleton = getOptionalObject(root, "skeleton");
        model.pose = getOptionalObject(root, "pose");
        model.shapeKeys = getOptionalObject(root, "shape_keys");
        model.ik = getOptionalObject(root, "ik");
        model.physics = getOptionalObject(root, "physics");
        model.actions = getOptionalObject(root, "actions");
        model.skeletonMapping = getOptionalObject(root, "skeleton_mapping");

        return model;
    }

    /**
     * 从 JSON 文本反序列化
     */
    public static BBSSModel fromJsonString(String json)
    {
        if (json == null || json.trim().isEmpty())
        {
            throw new IllegalArgumentException(".bbsm JSON 为空");
        }

        JsonElement element = JsonParser.parseString(json);

        if (!element.isJsonObject())
        {
            throw new IllegalArgumentException(".bbsm 根节点不是对象");
        }

        return fromJson(element.getAsJsonObject());
    }

    /**
     * 写入 .bbsm 文件（GZIP + JSON，单文件）
     *
     * @return 是否写入成功
     */
    public boolean writeToFile(File file) throws IOException
    {
        file.getParentFile().mkdirs();

        String json = this.toJsonString();

        try (GZIPOutputStream gzip = new GZIPOutputStream(new FileOutputStream(file));
             OutputStreamWriter writer = new OutputStreamWriter(gzip, StandardCharsets.UTF_8))
        {
            writer.write(json);
        }

        return true;
    }

    /**
     * 读取 .bbsm 文件（自动识别 GZIP 与纯 JSON 两种形态）
     */
    public static BBSSModel readFromFile(File file) throws IOException
    {
        byte[] raw = IOUtils.readBytes(new FileInputStream(file));

        String json;

        try
        {
            json = new String(gunzip(raw), StandardCharsets.UTF_8);
        }
        catch (IOException notGzip)
        {
            json = new String(raw, StandardCharsets.UTF_8);
        }

        return fromJsonString(json);
    }

    /**
     * 从输入流读取（GZIP 或纯 JSON）
     */
    public static BBSSModel readFromStream(InputStream stream) throws IOException
    {
        byte[] raw = IOUtils.readBytes(stream);

        try
        {
            return fromJsonString(new String(gunzip(raw), StandardCharsets.UTF_8));
        }
        catch (IOException notGzip)
        {
            return fromJsonString(new String(raw, StandardCharsets.UTF_8));
        }
    }

    /**
     * 便捷方法：从内嵌的 model.bobj 文本解析 BOBJData（无则返回 null）
     */
    public mchorse.bbs_mod.bobj.BOBJLoader.BOBJData parseBOBJ() throws Exception
    {
        String bobj = this.modelFiles.get("model.bobj");

        if (bobj == null || bobj.isEmpty())
        {
            return null;
        }

        return mchorse.bbs_mod.bobj.BOBJLoader.readData(new ByteArrayInputStream(bobj.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] gunzip(byte[] compressed) throws IOException
    {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed)))
        {
            return IOUtils.readBytes(gzip);
        }
    }

    private static String getString(JsonObject object, String key, String fallback)
    {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : fallback;
    }

    private static JsonObject getOptionalObject(JsonObject object, String key)
    {
        return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : null;
    }
}
