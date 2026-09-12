package mchorse.bbs_ai.model;

import com.google.gson.JsonObject;
import mchorse.bbs_ai.BBSAIStudio;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 游戏内模型导出器：{@link ModelForm} → .bbsm 单文件
 *
 * <p>导出为单个 {@code .bbsm}（GZIP + JSON）文件，内嵌全部数据（后续要求 1
 * 「导出时单文件导出和导入」）：
 * <ul>
 *   <li>原始模型文件（model.bobj / config.json / shape_keys.json 文本）</li>
 *   <li>纹理（主纹理 + 材质纹理，Base64 内嵌）</li>
 *   <li>解析后的几何与骨骼（外部工具可直读）</li>
 *   <li>完整 ModelForm 序列化（姿态 / 形态键 / IK / 物理 / 动作）【原版兼容，
 *       走 {@link FormUtils#toData}】</li>
 * </ul>
 * 缺失的资源（内置模型、纯色纹理等）跳过内嵌，不阻断导出。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ModelExporter
{
    /**
     * 导出结果摘要
     */
    public static class ExportResult
    {
        public File file;
        public int meshes;
        public int bones;
        public int vertices;
        public int textures;
        public long bytes;
        public boolean hasBOBJ;

        public String summary()
        {
            return (this.hasBOBJ ? this.meshes + " 网格 / " + this.bones + " 骨骼 / " + this.vertices + " 顶点 / " : "无几何（仅表单数据） / ")
                + this.textures + " 纹理 / " + (this.bytes / 1024) + " KB";
        }
    }

    /**
     * 导出 ModelForm 到 .bbsm 单文件
     *
     * @param form 要导出的模型表单
     * @param name 模型名称（空则回退表单显示名）
     * @param author 作者
     * @param description 描述
     * @param outputFile 输出文件（.bbsm）
     */
    public static ExportResult export(ModelForm form, String name, String author, String description, File outputFile) throws IOException
    {
        BBSSModel model = new BBSSModel();
        ExportResult result = new ExportResult();

        String displayName = name == null || name.isEmpty() ? form.getDisplayName() : name;

        model.stampGenerated(displayName, author, description, "bbs_ai_studio");
        model.modelId = form.model.get();

        /* === 完整表单数据（姿态 / 形态键 / IK / 物理 / 动作 / 变换…）=== */
        MapType formData = FormUtils.toData(form);

        if (formData != null)
        {
            model.form = (JsonObject) DataJson.toJson(formData);
        }

        /* === 分段冗余（外部工具与旧版本读取用）=== */
        MapType poseData = new MapType();

        form.pose.get().toData(poseData);
        model.pose = (JsonObject) DataJson.toJson(poseData);

        MapType shapeKeysData = new MapType();

        form.shapeKeys.get().toData(shapeKeysData);
        model.shapeKeys = (JsonObject) DataJson.toJson(shapeKeysData);

        /* bbs-fs 2.6 起 IK / 物理不再挂在 ModelForm 的 ik / physics 值上：
         * IK 数据随 bones 区块进入完整表单数据（上方 model.form），
         * 物理与约束则存于模型 config.json（下方内嵌 modelFiles），分段字段留空。 */

        MapType actionsData = new MapType();

        form.actions.get().toData(actionsData);
        model.actions = (JsonObject) DataJson.toJson(actionsData);

        /* === 骨骼映射（标准槽位 → 模型骨骼，自定义模型自动识别确认后挂载）=== */
        mchorse.bbs_ai.motion.SkeletonMapping mapping = mchorse.bbs_ai.motion.SkeletonMapping.get(form);

        if (!mapping.isEmpty())
        {
            model.skeletonMapping = (JsonObject) DataJson.toJson(mapping.toData());
        }

        /* === 原始模型文件内嵌（单文件往返零损失）=== */
        AssetProvider provider = BBSMod.getProvider();
        String modelId = form.model.get();

        if (modelId != null && !modelId.isEmpty())
        {
            Link modelFolder = Link.assets("models/" + modelId);

            for (String fileName : new String[] {"model.bobj", "config.json", "shape_keys.json"})
            {
                String content = readTextAsset(provider, modelFolder.combine(fileName));

                if (content != null)
                {
                    model.modelFiles.put(fileName, content);
                }
            }

            /* === 几何 + 骨骼（从 .bobj 解析）=== */
            String bobj = model.modelFiles.get("model.bobj");

            if (bobj != null)
            {
                try
                {
                    BOBJLoader.BOBJData data = model.parseBOBJ();
                    mchorse.bbs_mod.bobj.BOBJArmature armature = data == null ? null : firstArmature(data);

                    if (data != null)
                    {
                        model.geometry = BOBJWriter.geometryToJson(data);
                        result.hasBOBJ = true;
                        result.meshes = data.meshes.size();
                        result.vertices = data.vertices.size();
                        result.bones = armature == null ? 0 : armature.bones.size();

                        if (armature != null)
                        {
                            model.skeleton = BOBJWriter.skeletonToJson(armature);
                        }
                    }
                }
                catch (Exception e)
                {
                    System.err.println("[BBS AI] 导出：解析 .bobj 几何失败（保留原始文件内嵌）：" + e.getMessage());
                }
            }
        }

        /* === 纹理内嵌（主纹理 + 材质纹理）=== */
        if (embedTexture(model, provider, "main", form.texture.get()))
        {
            result.textures ++;
        }

        for (Map.Entry<String, Link> entry : new TreeMap<>(form.materialTextures.get()).entrySet())
        {
            if (embedTexture(model, provider, "material:" + entry.getKey(), entry.getValue()))
            {
                result.textures ++;
            }
        }

        /* === 落盘（GZIP + JSON 单文件）=== */
        model.writeToFile(outputFile);

        result.file = outputFile;
        result.bytes = outputFile.length();

        return result;
    }

    /**
     * 便捷导出：输出到默认目录 {@code config/bbs/models/<name>.bbsm}
     */
    public static ExportResult exportToDefaultFolder(ModelForm form, String name, String author, String description) throws IOException
    {
        String fileName = ModelImporter.sanitizeId(name == null || name.isEmpty() ? form.getDisplayName() : name) + ".bbsm";

        return export(form, name, author, description, BBSAIStudio.getModelExportPath(fileName));
    }

    /**
     * 内嵌一个纹理（仅 assets 源且可读取时）
     *
     * @return 是否成功内嵌
     */
    private static boolean embedTexture(BBSSModel model, AssetProvider provider, String role, Link link)
    {
        if (link == null || !Link.isAssets(link))
        {
            return false;
        }

        try (InputStream stream = provider.getAsset(link))
        {
            byte[] bytes = IOUtils.readBytes(stream);

            if (bytes.length == 0)
            {
                return false;
            }

            model.textures.add(new BBSSModel.TextureEntry(role, link.path, bytes));

            return true;
        }
        catch (IOException e)
        {
            return false;
        }
    }

    /**
     * 读取文本资源（缺失返回 null）
     */
    private static String readTextAsset(AssetProvider provider, Link link)
    {
        try (InputStream stream = provider.getAsset(link))
        {
            return IOUtils.readText(stream);
        }
        catch (IOException e)
        {
            return null;
        }
    }

    /**
     * 取第一个骨骼架（名称排序保证确定性）
     */
    private static mchorse.bbs_mod.bobj.BOBJArmature firstArmature(BOBJLoader.BOBJData data)
    {
        if (data.armatures.isEmpty())
        {
            return null;
        }

        List<String> names = new ArrayList<>(data.armatures.keySet());

        names.sort(String::compareTo);

        return data.armatures.get(names.get(0));
    }
}
