package mchorse.bbs_ai.motion;

import mchorse.bbs_mod.bobj.BOBJArmature;
import mchorse.bbs_mod.bobj.BOBJBone;
import mchorse.bbs_mod.bobj.BOBJLoader;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.settings.values.ui.ValueStringMap;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 骨骼映射：视频识别输出的标准六骨骼槽位 → 自定义模型的实际骨骼名
 *
 * <p>SkeletonMapper 与数据契约（{@code bbs_ai_studio_motion_v1}）始终使用标准槽位
 * （{@code head / body / left_arm / right_arm / left_leg / right_leg}），本类只在
 * <b>烘焙写入 Film 的那一刻</b>把标准槽位翻译成目标模型的骨骼名——标准玩家模型
 * 不受影响（无映射时槽位名即骨骼名），自定义模型则由映射决定写入哪根骨骼。</p>
 *
 * <p>映射以 {@code bbs_ai:skeleton_mapping}（命名空间化）挂在 {@link ModelForm}
 * 上随表单数据序列化【原版兼容】：2.6 起未加载插件的同名数据在保存时也会被
 * ValueGroup 的 foreign-key 机制原样保留。</p>
 *
 * <p>自动识别 {@link #autoDetect(Collection)} 按 BBS / Blockbuster / Blender 等
 * 常见骨骼命名约定猜测槽位，识别结果始终交给用户在骨骼映射面板检查、修改后确认。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class SkeletonMapping
{
    /**
     * 挂在 ModelForm 上的值 id（命名空间化：卸载本插件后数据仍会保留）
     */
    public static final String FORM_VALUE_ID = "bbs_ai:skeleton_mapping";

    /**
     * 自动识别置信度：完全猜不出任何槽位时返回 null（视为"无需映射/无法识别"）
     */
    private static final int AUTO_DETECT_MIN_HITS = 2;

    /**
     * 槽位 → 模型骨骼名（有序；缺失槽位不写 Film，回退标准名）
     */
    private final Map<String, String> slots = new LinkedHashMap<>();

    /**
     * 空映射（所有槽位回退标准名，即原版标准玩家行为）
     */
    public static SkeletonMapping empty()
    {
        return new SkeletonMapping();
    }

    /**
     * 翻译单个骨骼名：有映射返回映射后的名字，否则原样返回
     */
    public String translate(String standardBone)
    {
        String mapped = this.slots.get(standardBone);

        return mapped == null || mapped.isEmpty() ? standardBone : mapped;
    }

    /**
     * 设置槽位映射（骨骼名为空/null 表示取消该槽位映射）
     */
    public void setSlot(String standardSlot, String modelBone)
    {
        if (modelBone == null || modelBone.isEmpty())
        {
            this.slots.remove(standardSlot);
        }
        else
        {
            this.slots.put(standardSlot, modelBone);
        }
    }

    /**
     * 获取槽位当前映射的模型骨骼名（无映射返回 null）
     */
    public String getSlot(String standardSlot)
    {
        return this.slots.get(standardSlot);
    }

    /**
     * 全部槽位映射（只读使用）
     */
    public Map<String, String> getSlots()
    {
        return new LinkedHashMap<>(this.slots);
    }

    /**
     * 是否没有任何映射
     */
    public boolean isEmpty()
    {
        return this.slots.isEmpty();
    }

    /* ====================================================================
     * ModelForm 挂载 / 读取【原版兼容】
     * ==================================================================== */

    /**
     * 把映射挂到表单（覆盖旧值；空映射则移除挂载值）
     */
    public void attach(ModelForm form)
    {
        ValueStringMap value = this.getOrCreateValue(form);

        value.set(new LinkedHashMap<>(this.slots));

        if (this.slots.isEmpty())
        {
            form.remove(value);
        }
    }

    /**
     * 读取表单上挂载的映射（无挂载返回空映射）
     */
    public static SkeletonMapping get(ModelForm form)
    {
        SkeletonMapping mapping = new SkeletonMapping();

        if (form != null)
        {
            if (form.getBasic(FORM_VALUE_ID) instanceof ValueStringMap value)
            {
                mapping.slots.putAll(value.get());
            }
        }

        return mapping;
    }

    /**
     * 生成/获取表单上的映射值【原版兼容】
     */
    private static ValueStringMap getOrCreateValue(ModelForm form)
    {
        if (form.getBasic(FORM_VALUE_ID) instanceof ValueStringMap existing)
        {
            return existing;
        }

        ValueStringMap value = new ValueStringMap(FORM_VALUE_ID);

        form.add(value);

        return value;
    }

    /**
     * 静态翻译工具：按表单挂载的映射翻译骨骼名（无映射原样返回）
     */
    public static String translateBone(ModelForm form, String standardBone)
    {
        if (form != null && form.getBasic(FORM_VALUE_ID) instanceof ValueStringMap value)
        {
            String mapped = value.get().get(standardBone);

            if (mapped != null && !mapped.isEmpty())
            {
                return mapped;
            }
        }

        return standardBone;
    }

    /* ====================================================================
     * 序列化（MapType，用于 .bbsm 携带）
     * ==================================================================== */

    /**
     * 序列化为 MapType
     */
    public MapType toData()
    {
        MapType map = new MapType();

        for (Map.Entry<String, String> entry : this.slots.entrySet())
        {
            map.putString(entry.getKey(), entry.getValue());
        }

        return map;
    }

    /**
     * 从 MapType 恢复
     */
    public static SkeletonMapping fromData(MapType data)
    {
        SkeletonMapping mapping = new SkeletonMapping();

        if (data != null)
        {
            for (Map.Entry<String, mchorse.bbs_mod.data.types.BaseType> entry : data.asMap())
            {
                if (entry.getValue().isString())
                {
                    mapping.slots.put(entry.getKey(), entry.getValue().asString());
                }
            }
        }

        return mapping;
    }

    /* ====================================================================
     * 骨骼名提取（.bobj 解析）
     * ==================================================================== */

    /**
     * 从模型表单对应的 .bobj 提取全部骨骼名（按骨骼索引序）
     *
     * @param form 模型表单（读取 model 路径下的 model.bobj）
     * @return 骨骼名列表；模型无骨骼 / 加载失败返回空列表
     */
    public static List<String> extractBoneNames(ModelForm form)
    {
        List<String> names = new ArrayList<>();

        if (form == null)
        {
            return names;
        }

        try
        {
            String modelId = form.model.get();

            if (modelId == null || modelId.isEmpty())
            {
                return names;
            }

            String bobj = mchorse.bbs_mod.utils.IOUtils.readText(
                mchorse.bbs_mod.BBSMod.getProvider().getAsset(
                    mchorse.bbs_mod.resources.Link.assets("models/" + modelId + "/model.bobj")
                )
            );

            if (bobj == null || bobj.isEmpty())
            {
                return names;
            }

            BOBJLoader.BOBJData data = BOBJLoader.readData(
                new ByteArrayInputStream(bobj.getBytes(StandardCharsets.UTF_8))
            );

            for (BOBJArmature armature : data.armatures.values())
            {
                for (BOBJBone bone : armature.orderedBones)
                {
                    if (!names.contains(bone.name))
                    {
                        names.add(bone.name);
                    }
                }
            }
        }
        catch (Exception e)
        {
            System.err.println("[BBS AI] 读取模型骨骼失败（映射自动识别不可用）：" + e.getMessage());
        }

        return names;
    }

    /* ====================================================================
     * 自动识别（命名约定匹配）
     * ==================================================================== */

    /**
     * 按命名约定自动猜测槽位映射
     *
     * <p>匹配优先级：完全同名 > 别名表 > 侧向后缀变体（{@code _l} / {@code .L} 等）。
     * 命中槽位少于 {@link #AUTO_DETECT_MIN_HITS} 个时返回 null（表示猜不出，让用户手动配）。</p>
     *
     * @param modelBones 模型的全部骨骼名
     * @return 自动识别结果；无法识别返回 null
     */
    public static SkeletonMapping autoDetect(Collection<String> modelBones)
    {
        if (modelBones == null || modelBones.isEmpty())
        {
            return null;
        }

        /* 归一化名字 → 原名 */
        Map<String, String> normalized = new LinkedHashMap<>();

        for (String bone : modelBones)
        {
            normalized.put(normalize(bone), bone);
        }

        SkeletonMapping mapping = new SkeletonMapping();
        int hits = 0;

        for (String slot : SkeletonMapper.ALL_BONES)
        {
            String matched = matchSlot(slot, normalized);

            if (matched != null)
            {
                mapping.setSlot(slot, matched);
                hits++;
            }
        }

        return hits >= AUTO_DETECT_MIN_HITS ? mapping : null;
    }

    /**
     * 为单个槽位在归一化骨骼名中找最佳匹配
     */
    private static String matchSlot(String slot, Map<String, String> normalized)
    {
        /* 1. 完全同名（归一化后） */
        String direct = normalized.get(normalize(slot));

        if (direct != null)
        {
            return direct;
        }

        /* 2. 别名表逐个尝试（含侧向组合） */
        for (String alias : aliasesFor(slot))
        {
            String hit = normalized.get(alias);

            if (hit != null)
            {
                return hit;
            }
        }

        /* 3. 归一化名字包含别名（如 upper_arm_l 包含 arm_l） */
        for (String alias : aliasesFor(slot))
        {
            for (Map.Entry<String, String> entry : normalized.entrySet())
            {
                if (entry.getKey().contains(alias))
                {
                    return entry.getValue();
                }
            }
        }

        return null;
    }

    /**
     * 槽位的全部识别别名（归一化形式）
     */
    private static List<String> aliasesFor(String slot)
    {
        List<String> aliases = new ArrayList<>();
        boolean left = slot.startsWith("left");
        boolean right = slot.startsWith("right");
        String side = left ? "l" : right ? "r" : "";
        String sideFull = left ? "left" : right ? "right" : "";
        String part = left || right ? slot.substring(slot.indexOf('_') + 1) : slot;

        if (left || right)
        {
            /* l_arm / arm_l / leftarm / arm_left ...（全部为归一化形式，与 normalize 键直接对齐） */
            aliases.add(side + "_" + part);
            aliases.add(part + "_" + side);
            aliases.add(side + part);
            aliases.add(sideFull + part);
            aliases.add(part + "_" + sideFull);
            aliases.add(sideFull + "_" + part);
        }
        else
        {
            switch (slot)
            {
                case SkeletonMapper.HEAD:
                    aliases.add("skull");
                    aliases.add("head_main");
                    aliases.add("hat");
                    aliases.add("cap");
                    break;
                case SkeletonMapper.BODY:
                    aliases.add("torso");
                    aliases.add("chest");
                    aliases.add("spine");
                    aliases.add("upper_body");
                    aliases.add("core");
                    aliases.add("trunk");
                    break;
            }
        }

        return aliases;
    }

    /**
     * 归一化：小写，去掉装饰前缀，统一分隔符（点/空格/连字符 → 下划线），
     * 去掉 root/armature 等结构骨骼的常见前缀噪声
     */
    private static String normalize(String name)
    {
        String n = name.toLowerCase(Locale.ROOT).replace('.', '_').replace(' ', '_').replace('-', '_');

        /* Blender 的 upper_arm.L 等已由点号转换；去掉转轴/变形后缀 */
        n = n.replaceAll("_+$", "");

        return n;
    }

    /**
     * 调试用：可读形式
     */
    @Override
    public String toString()
    {
        return "SkeletonMapping" + this.slots;
    }
}
