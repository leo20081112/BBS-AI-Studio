package mchorse.bbs_ai.model;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.utils.pose.Pose;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;

/**
 * 游戏内模型导入器：.bbsm / .bbs.json / .bobj → {@link ModelForm}
 *
 * <p>单文件导入（后续要求 1）：一个 .bbsm 文件包含几何、骨骼、纹理、姿态、IK、物理、
 * 动作的全部数据，导入时自动解包到 {@code config/bbs/assets/models/<id>/}：
 * <ol>
 *   <li>内嵌的 model.bobj / config.json / shape_keys.json 原样写出；
 *       外部工具生成的 .bbsm（无 model.bobj）用 {@link BOBJWriter} 从几何段现场合成</li>
 *   <li>内嵌纹理（Base64）写出并重建 Link（主纹理 + 材质纹理）</li>
 *   <li>完整 ModelForm 数据经 {@link FormUtils#fromData} 重建【原版兼容】，模型 ID 与
 *       纹理链接重定向到解包目录</li>
 *   <li>.bbs.json（Blockbench）与裸 .bobj 导入为对应模型目录 + 最小 ModelForm</li>
 * </ol>
 * 客户端调用方在导入后需触发模型管理器重载（如 {@code BBSModClient.getModels().reload()}）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ModelImporter
{
    /**
     * 支持的导入格式
     */
    public enum ImportFormat
    {
        BBSM("bbsm"),
        BBS_JSON("bbs.json"),
        BOBJ("bobj");

        public final String label;

        ImportFormat(String label)
        {
            this.label = label;
        }
    }

    /**
     * 按扩展名识别格式（未知抛异常）
     */
    public static ImportFormat detectFormat(File file)
    {
        String name = file.getName().toLowerCase(Locale.ROOT);

        if (name.endsWith(".bbsm"))
        {
            return ImportFormat.BBSM;
        }

        if (name.endsWith(".bbs.json"))
        {
            return ImportFormat.BBS_JSON;
        }

        if (name.endsWith(".bobj"))
        {
            return ImportFormat.BOBJ;
        }

        throw new IllegalArgumentException("不支持的模型格式: " + file.getName() + "（支持 .bbsm / .bbs.json / .bobj）");
    }

    /**
     * 导入模型文件（自动识别格式；模型 ID 取文件名）
     */
    public static ModelForm importFromFile(File file) throws IOException
    {
        return importFromFile(file, null);
    }

    /**
     * 导入模型文件（自动识别格式）
     *
     * @param desiredId 期望的模型 ID（空则由文件名 / 元数据推导；冲突时自动追加序号）
     */
    public static ModelForm importFromFile(File file, String desiredId) throws IOException
    {
        if (file == null || !file.isFile())
        {
            throw new IllegalArgumentException("模型文件不存在: " + file);
        }

        ImportFormat format = detectFormat(file);

        return switch (format)
        {
            case BBSM -> importBBSM(file, desiredId);
            case BBS_JSON -> importBBSJson(file, desiredId);
            case BOBJ -> importBOBJ(file, desiredId);
        };
    }

    /**
     * 导入 .bbsm 单文件
     */
    public static ModelForm importBBSM(File file, String desiredId) throws IOException
    {
        BBSSModel model = BBSSModel.readFromFile(file);

        String fallbackId = !model.modelId.isEmpty() ? model.modelId : stripExtension(file.getName(), ".bbsm");
        String id = uniqueModelId(firstNonEmpty(desiredId, model.name, fallbackId));
        File folder = BBSMod.getAssetsPath("models/" + id);

        folder.mkdirs();

        /* === 1. 模型几何：内嵌 model.bobj 优先，缺失时从几何段合成 === */
        String bobj = model.modelFiles.get("model.bobj");

        if ((bobj == null || bobj.isEmpty()) && model.geometry != null)
        {
            bobj = BOBJWriter.writeBOBJ(model.geometry, model.skeleton);
        }

        if (bobj == null || bobj.isEmpty())
        {
            if (model.form == null)
            {
                throw new IOException(".bbsm 既无几何数据也无表单数据，无法导入: " + file.getName());
            }
        }
        else
        {
            writeTextFile(new File(folder, "model.bobj"), bobj);
        }

        /* === 2. 其余内嵌模型文件（config.json / shape_keys.json …）=== */
        for (Map.Entry<String, String> entry : model.modelFiles.entrySet())
        {
            if (entry.getKey().equals("model.bobj") || !isSafeFileName(entry.getKey()))
            {
                continue;
            }

            writeTextFile(new File(folder, entry.getKey()), entry.getValue());
        }

        /* === 3. 纹理解包 === */
        String mainTextureLink = null;

        for (BBSSModel.TextureEntry texture : model.textures)
        {
            if (texture.content == null || texture.content.isEmpty())
            {
                continue;
            }

            String sub = subPathFor(texture.path, model.modelId);
            File target = new File(folder, sub);

            target.getParentFile().mkdirs();
            writeBytes(target, texture.decode());

            if ("main".equals(texture.role))
            {
                mainTextureLink = "models/" + id + "/" + sub;
            }
        }

        /* === 4. 重建 ModelForm === */
        ModelForm form = rebuildForm(model);

        form.model.set(id);

        if (mainTextureLink != null)
        {
            form.texture.set(Link.assets(mainTextureLink));
        }
        else
        {
            remapTexture(form.texture.get(), model.modelId, id);
        }

        for (Map.Entry<String, Link> entry : form.materialTextures.get().entrySet())
        {
            Link remapped = remapTexture(entry.getValue(), model.modelId, id);

            if (remapped != entry.getValue())
            {
                form.materialTextures.setLink(entry.getKey(), remapped);
            }
        }

        /* 内嵌材质纹理的 Link 指向解包路径 */
        for (BBSSModel.TextureEntry texture : model.textures)
        {
            if (texture.role != null && texture.role.startsWith("material:") && texture.content != null && !texture.content.isEmpty())
            {
                String key = texture.role.substring("material:".length());

                form.materialTextures.setLink(key, Link.assets("models/" + id + "/" + subPathFor(texture.path, model.modelId)));
            }
        }

        if (!model.name.isEmpty())
        {
            form.name.set(model.name);
        }

        return form;
    }

    /**
     * 导入 .bbs.json（Blockbench 导出；需与同名 .png 放一起）
     */
    public static ModelForm importBBSJson(File file, String desiredId) throws IOException
    {
        String id = uniqueModelId(firstNonEmpty(desiredId, stripExtension(file.getName(), ".bbs.json")));
        File folder = BBSMod.getAssetsPath("models/" + id);

        folder.mkdirs();

        Files.copy(file.toPath(), new File(folder, "model.bbs.json").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        ModelForm form = new ModelForm();

        form.model.set(id);

        /* 配对纹理：同名 .png → texture.png → model.png */
        for (String candidate : new String[]
        {
            stripExtension(file.getName(), ".bbs.json") + ".png",
            "texture.png",
            "model.png"
        })
        {
            File sibling = new File(file.getParentFile(), candidate);

            if (sibling.isFile())
            {
                Files.copy(sibling.toPath(), new File(folder, candidate).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                form.texture.set(Link.assets("models/" + id + "/" + candidate));

                break;
            }
        }

        return form;
    }

    /**
     * 导入裸 .bobj
     */
    public static ModelForm importBOBJ(File file, String desiredId) throws IOException
    {
        String id = uniqueModelId(firstNonEmpty(desiredId, stripExtension(file.getName(), ".bobj")));
        File folder = BBSMod.getAssetsPath("models/" + id);

        folder.mkdirs();

        Files.copy(file.toPath(), new File(folder, "model.bobj").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        ModelForm form = new ModelForm();

        form.model.set(id);

        return form;
    }

    /**
     * 从 .bbsm 重建 ModelForm：优先完整表单数据，缺失时分段填充
     */
    private static ModelForm rebuildForm(BBSSModel model)
    {
        ModelForm form = null;

        if (model.form != null)
        {
            BaseType data = DataJson.fromJson(model.form);

            if (data instanceof MapType mapData)
            {
                form = FormUtils.fromData(mapData) instanceof ModelForm modelForm ? modelForm : null;
            }
        }

        if (form == null)
        {
            form = new ModelForm();
        }

        if (model.pose != null)
        {
            BaseType poseData = DataJson.fromJson(model.pose);

            if (poseData instanceof MapType map)
            {
                Pose pose = new Pose();

                pose.fromData(map);
                form.pose.set(pose);
            }
        }

        if (model.shapeKeys != null)
        {
            BaseType keysData = DataJson.fromJson(model.shapeKeys);

            if (keysData instanceof MapType map)
            {
                mchorse.bbs_mod.obj.shapes.ShapeKeys keys = new mchorse.bbs_mod.obj.shapes.ShapeKeys();

                keys.fromData(map);
                form.shapeKeys.set(keys);
            }
        }

        /* bbs-fs 2.6 起 ModelForm 不再有 ik / physics 值：IK 随表单 bones 区块恢复，
         * 物理与约束由内嵌 config.json 原样写回模型目录时恢复，无需单独赋值。 */

        /* 骨骼映射：.bbsm 携带的标准槽位 → 模型骨骼映射挂回表单，
         * 供视频识别烘焙写入正确骨骼（旧 .bbsm 无此字段则保持标准名）。 */
        if (model.skeletonMapping != null)
        {
            BaseType mappingData = DataJson.fromJson(model.skeletonMapping);

            if (mappingData instanceof MapType mappingMap)
            {
                mchorse.bbs_ai.motion.SkeletonMapping.fromData(mappingMap).attach(form);
            }
        }

        if (model.actions != null)
        {
            BaseType actionsData = DataJson.fromJson(model.actions);

            if (actionsData instanceof MapType map)
            {
                ActionsConfig actions = new ActionsConfig();

                actions.fromData(map);
                form.actions.set(actions);
            }
        }

        return form;
    }

    /**
     * 未内嵌纹理的链接重定向：models/&lt;oldId&gt;/… → models/&lt;newId&gt;/…
     *
     * @return 新链接（需要重定向时）或原链接
     */
    private static Link remapTexture(Link link, String oldId, String newId)
    {
        if (link == null || !Link.isAssets(link) || oldId == null || oldId.isEmpty())
        {
            return link;
        }

        String prefix = "models/" + oldId + "/";

        if (link.path.startsWith(prefix))
        {
            return Link.assets("models/" + newId + "/" + link.path.substring(prefix.length()));
        }

        return link;
    }

    /**
     * 纹理子路径：相对 models/&lt;modelId&gt;/ 的部分；不含前缀时取文件名
     */
    private static String subPathFor(String path, String modelId)
    {
        if (path == null || path.isEmpty())
        {
            return "texture.png";
        }

        String prefix = modelId == null || modelId.isEmpty() ? "" : "models/" + modelId + "/";
        String sub = !prefix.isEmpty() && path.startsWith(prefix) ? path.substring(prefix.length()) : path;

        /* 只保留安全字符（防路径穿越） */
        sub = sub.replace("\\", "/");

        String[] parts = sub.split("/");
        StringBuilder builder = new StringBuilder();

        for (String part : parts)
        {
            if (part.isEmpty() || part.equals(".") || part.equals(".."))
            {
                continue;
            }

            if (builder.length() > 0)
            {
                builder.append("/");
            }

            builder.append(part);
        }

        return builder.length() == 0 ? "texture.png" : builder.toString();
    }

    /**
     * 文件名是否安全（无路径分隔符、无穿越）
     */
    private static boolean isSafeFileName(String name)
    {
        return name != null && !name.isEmpty() && !name.contains("/") && !name.contains("\\") && !name.contains("..");
    }

    /**
     * 模型 ID 净化：剔除文件系统非法字符
     */
    public static String sanitizeId(String raw)
    {
        String id = raw == null ? "" : raw.replaceAll("[\\\\/:*?\"<>|\\s]+", "_").replaceAll("^\\.+", "");

        if (id.length() > 64)
        {
            id = id.substring(0, 64);
        }

        return id.isEmpty() ? "imported_model" : id;
    }

    /**
     * 在 assets/models/ 下分配不冲突的模型 ID（冲突时追加 _2 / _3 …）
     */
    public static String uniqueModelId(String raw)
    {
        String base = sanitizeId(raw);
        String id = base;
        int counter = 2;

        while (BBSMod.getAssetsPath("models/" + id).exists())
        {
            id = base + "_" + counter;
            counter ++;
        }

        return id;
    }

    private static String firstNonEmpty(String... values)
    {
        for (String value : values)
        {
            if (value != null && !value.isEmpty())
            {
                return value;
            }
        }

        return "";
    }

    private static String stripExtension(String name, String extension)
    {
        return name.toLowerCase(Locale.ROOT).endsWith(extension) ? name.substring(0, name.length() - extension.length()) : name;
    }

    private static void writeTextFile(File file, String content) throws IOException
    {
        writeBytes(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytes(File file, byte[] bytes) throws IOException
    {
        try (OutputStream out = new FileOutputStream(file))
        {
            out.write(bytes);
        }
    }
}
