package mchorse.bbs_ai.ui.hotkey;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 热键注册表（客户端，单例）
 *
 * <p>注册项目规范定义的全部热键（视图 / 变换 / 关键帧 / 时间轴 / 面板 / 模式 / AI / 通用
 * 八大类），支持用户自定义修改、分类查询与冲突检测。自定义结果持久化到
 * {@code config/bbs/settings/bbs_ai_hotkeys.json}（经 {@link KeyMapConfig}）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class HotkeyRegistry
{
    /**
     * 单例
     */
    private static HotkeyRegistry instance;

    /**
     * 动作 ID → 定义（保持注册顺序）
     */
    private final Map<String, HotkeyDefinition> hotkeys = new LinkedHashMap<>();

    /**
     * 初始化并注册全部默认热键
     */
    public static synchronized void initialize()
    {
        if (instance != null)
        {
            return;
        }

        instance = new HotkeyRegistry();
        instance.registerDefaults();

        /* 从磁盘恢复用户自定义 */
        KeyMapConfig.load(instance);

        System.out.println("[BBS AI] 热键注册完成（" + instance.hotkeys.size() + " 个）");
    }

    /**
     * 获取单例
     */
    public static HotkeyRegistry get()
    {
        if (instance == null)
        {
            initialize();
        }

        return instance;
    }

    /**
     * 注册项目规范中的完整热键列表
     */
    private void registerDefaults()
    {
        /* ---- 视图 ---- */
        this.add("view.orbit", "轨道旋转", "按住鼠标中键拖拽旋转视图", HotkeyDefinition.Category.VIEW, HotkeyDefinition.mouseKey(1), 0);
        this.add("view.pan", "平移视图", "按住 Shift + 鼠标中键拖拽平移", HotkeyDefinition.Category.VIEW, HotkeyDefinition.mouseKey(1), HotkeyDefinition.MOD_SHIFT);
        this.add("view.zoom", "缩放视图", "滚动鼠标滚轮缩放", HotkeyDefinition.Category.VIEW, HotkeyDefinition.mouseKey(-1), 0);
        this.add("view.focus", "聚焦选中", "将视图中心移到选中物体", HotkeyDefinition.Category.VIEW, GLFW.GLFW_KEY_KP_DECIMAL, 0);
        this.add("view.front", "前视图", "切换到前视图", HotkeyDefinition.Category.VIEW, GLFW.GLFW_KEY_KP_1, 0);
        this.add("view.right", "右视图", "切换到右视图", HotkeyDefinition.Category.VIEW, GLFW.GLFW_KEY_KP_3, 0);
        this.add("view.top", "顶视图", "切换到顶视图", HotkeyDefinition.Category.VIEW, GLFW.GLFW_KEY_KP_7, 0);
        this.add("view.ortho", "正交切换", "正交/透视切换", HotkeyDefinition.Category.VIEW, GLFW.GLFW_KEY_KP_5, 0);
        this.add("view.camera", "摄像机视图", "切换到摄像机视角", HotkeyDefinition.Category.VIEW, GLFW.GLFW_KEY_KP_0, 0);

        /* ---- 变换 ---- */
        this.add("transform.grab", "移动", "G 键进入移动模式，移动鼠标确认，左键确认，右键取消", HotkeyDefinition.Category.TRANSFORM, GLFW.GLFW_KEY_G, 0);
        this.add("transform.rotate", "旋转", "R 键进入旋转模式，水平拖拽控制角度", HotkeyDefinition.Category.TRANSFORM, GLFW.GLFW_KEY_R, 0);
        this.add("transform.scale", "缩放", "S 键进入缩放模式，水平拖拽控制比例", HotkeyDefinition.Category.TRANSFORM, GLFW.GLFW_KEY_S, 0);
        this.add("transform.confirm", "确认变换", "确认当前变换操作", HotkeyDefinition.Category.TRANSFORM, GLFW.GLFW_KEY_ENTER, 0);
        this.add("transform.cancel", "取消变换", "取消当前变换，恢复原状", HotkeyDefinition.Category.TRANSFORM, GLFW.GLFW_KEY_ESCAPE, 0);
        this.add("transform.axis_x", "X轴约束", "变换中按 X 约束到 X 轴，再按解除", HotkeyDefinition.Category.TRANSFORM, GLFW.GLFW_KEY_X, 0);
        this.add("transform.axis_y", "Y轴约束", "变换中按 Y 约束到 Y 轴，再按解除", HotkeyDefinition.Category.TRANSFORM, GLFW.GLFW_KEY_Y, 0);
        this.add("transform.axis_z", "Z轴约束", "变换中按 Z 约束到 Z 轴，再按解除", HotkeyDefinition.Category.TRANSFORM, GLFW.GLFW_KEY_Z, 0);
        this.add("transform.precise", "精确模式", "按住 Shift 进行精确微调（速度降为 1/10）", HotkeyDefinition.Category.TRANSFORM, GLFW.GLFW_KEY_LEFT_SHIFT, 0);

        /* ---- 关键帧 ---- */
        this.add("keyframe.insert", "插入关键帧", "根据当前选中属性插入关键帧", HotkeyDefinition.Category.KEYFRAME, GLFW.GLFW_KEY_I, 0);
        this.add("keyframe.delete", "删除关键帧", "删除当前帧的关键帧", HotkeyDefinition.Category.KEYFRAME, GLFW.GLFW_KEY_DELETE, 0);
        this.add("keyframe.copy", "复制关键帧", "复制选中的关键帧", HotkeyDefinition.Category.KEYFRAME, GLFW.GLFW_KEY_C, HotkeyDefinition.MOD_CTRL);
        this.add("keyframe.paste", "粘贴关键帧", "粘贴关键帧到当前位置", HotkeyDefinition.Category.KEYFRAME, GLFW.GLFW_KEY_V, HotkeyDefinition.MOD_CTRL);
        this.add("keyframe.duplicate", "复制并变换", "复制选中对象并进入变换", HotkeyDefinition.Category.KEYFRAME, GLFW.GLFW_KEY_D, HotkeyDefinition.MOD_SHIFT);
        this.add("keyframe.select_all", "全选", "全选/取消全选关键帧", HotkeyDefinition.Category.KEYFRAME, GLFW.GLFW_KEY_A, 0);
        this.add("keyframe.box_select", "框选", "进入框选模式", HotkeyDefinition.Category.KEYFRAME, GLFW.GLFW_KEY_B, 0);

        /* ---- 时间轴 ---- */
        this.add("timeline.play", "播放/暂停", "播放或暂停动画", HotkeyDefinition.Category.TIMELINE, GLFW.GLFW_KEY_SPACE, 0);
        this.add("timeline.prev_frame", "上一帧", "后退一帧", HotkeyDefinition.Category.TIMELINE, GLFW.GLFW_KEY_LEFT, 0);
        this.add("timeline.next_frame", "下一帧", "前进一帧", HotkeyDefinition.Category.TIMELINE, GLFW.GLFW_KEY_RIGHT, 0);
        this.add("timeline.prev_keyframe", "上一关键帧", "跳转到上一关键帧", HotkeyDefinition.Category.TIMELINE, GLFW.GLFW_KEY_LEFT, HotkeyDefinition.MOD_SHIFT);
        this.add("timeline.next_keyframe", "下一关键帧", "跳转到下一关键帧", HotkeyDefinition.Category.TIMELINE, GLFW.GLFW_KEY_RIGHT, HotkeyDefinition.MOD_SHIFT);
        this.add("timeline.home", "跳到开头", "跳转到第一帧", HotkeyDefinition.Category.TIMELINE, GLFW.GLFW_KEY_HOME, 0);
        this.add("timeline.end", "跳到结尾", "跳转到最后帧", HotkeyDefinition.Category.TIMELINE, GLFW.GLFW_KEY_END, 0);
        this.add("timeline.reverse", "倒放", "J 键倒放", HotkeyDefinition.Category.TIMELINE, GLFW.GLFW_KEY_J, 0);
        this.add("timeline.pause", "暂停", "K 键暂停", HotkeyDefinition.Category.TIMELINE, GLFW.GLFW_KEY_K, 0);
        this.add("timeline.forward", "正放", "L 键正放，多次按加速", HotkeyDefinition.Category.TIMELINE, GLFW.GLFW_KEY_L, 0);

        /* ---- 面板 ---- */
        this.add("panel.n_panel", "属性面板", "切换右侧属性面板 (N-Panel)", HotkeyDefinition.Category.PANEL, GLFW.GLFW_KEY_N, 0);
        this.add("panel.toolbar", "工具架", "切换左侧工具架", HotkeyDefinition.Category.PANEL, GLFW.GLFW_KEY_T, 0);
        this.add("panel.outliner", "大纲视图", "切换大纲视图", HotkeyDefinition.Category.PANEL, GLFW.GLFW_KEY_F9, 0);
        this.add("panel.timeline", "时间轴面板", "切换时间轴面板", HotkeyDefinition.Category.PANEL, GLFW.GLFW_KEY_F10, 0);

        /* ---- 模式 ---- */
        this.add("mode.object", "对象模式", "切换到对象模式", HotkeyDefinition.Category.MODE, GLFW.GLFW_KEY_TAB, 0);
        this.add("mode.pose", "姿态模式", "切换到姿态模式（用于 IK）", HotkeyDefinition.Category.MODE, GLFW.GLFW_KEY_TAB, HotkeyDefinition.MOD_CTRL);

        /* ---- AI ---- */
        this.add("ai.recognize", "快速识别", "启动游戏内视频骨骼识别", HotkeyDefinition.Category.AI, GLFW.GLFW_KEY_M, HotkeyDefinition.MOD_SHIFT | HotkeyDefinition.MOD_CTRL);
        this.add("ai.import_panel", "导入面板", "打开动作导入面板", HotkeyDefinition.Category.AI, GLFW.GLFW_KEY_I, HotkeyDefinition.MOD_SHIFT | HotkeyDefinition.MOD_CTRL);
        this.add("ai.storyboard", "分镜生成", "打开分镜文本生成", HotkeyDefinition.Category.AI, GLFW.GLFW_KEY_S, HotkeyDefinition.MOD_SHIFT | HotkeyDefinition.MOD_CTRL);
        this.add("ai.preview_enter", "进入预览", "进入预烘焙预览模式", HotkeyDefinition.Category.AI, GLFW.GLFW_KEY_P, 0);
        this.add("ai.preview_bake", "确认烘焙", "确认烘焙预览数据", HotkeyDefinition.Category.AI, GLFW.GLFW_KEY_ENTER, HotkeyDefinition.MOD_CTRL);
        this.add("ai.preview_discard", "放弃预览", "放弃预览数据", HotkeyDefinition.Category.AI, GLFW.GLFW_KEY_ESCAPE, HotkeyDefinition.MOD_CTRL);

        /* ---- 通用 ---- */
        this.add("general.undo", "撤销", "撤销上一步操作", HotkeyDefinition.Category.GENERAL, GLFW.GLFW_KEY_Z, HotkeyDefinition.MOD_CTRL);
        this.add("general.redo", "重做", "重做撤销的操作", HotkeyDefinition.Category.GENERAL, GLFW.GLFW_KEY_Z, HotkeyDefinition.MOD_SHIFT | HotkeyDefinition.MOD_CTRL);
        this.add("general.save", "保存", "保存当前项目", HotkeyDefinition.Category.GENERAL, GLFW.GLFW_KEY_S, HotkeyDefinition.MOD_CTRL);
        this.add("general.help", "热键速查表", "按 F1 随时打开热键速查表", HotkeyDefinition.Category.GENERAL, GLFW.GLFW_KEY_F1, 0);
    }

    /**
     * 注册热键
     */
    private void add(String id, String name, String description, HotkeyDefinition.Category category, int key, int mods)
    {
        this.hotkeys.put(id, new HotkeyDefinition(id, name, description, category, key, mods));
    }

    /**
     * 获取热键定义
     */
    public HotkeyDefinition get(String id)
    {
        return this.hotkeys.get(id);
    }

    /**
     * 全部热键（注册顺序）
     */
    public List<HotkeyDefinition> getAll()
    {
        return new ArrayList<>(this.hotkeys.values());
    }

    /**
     * 按分类查询
     */
    public List<HotkeyDefinition> getByCategory(HotkeyDefinition.Category category)
    {
        List<HotkeyDefinition> result = new ArrayList<>();

        for (HotkeyDefinition definition : this.hotkeys.values())
        {
            if (definition.category == category)
            {
                result.add(definition);
            }
        }

        return result;
    }

    /**
     * 全部分类
     */
    public HotkeyDefinition.Category[] getCategories()
    {
        return HotkeyDefinition.Category.values();
    }

    /**
     * 修改热键（含冲突检测）
     *
     * @return 冲突的动作 ID；无冲突为 null
     */
    public String rebind(String id, int key, int mods)
    {
        /* 冲突检测：两个动作不能绑定相同热键 */
        for (HotkeyDefinition other : this.hotkeys.values())
        {
            if (!other.id.equals(id) && other.key == key && other.mods == mods)
            {
                return other.id;
            }
        }

        HotkeyDefinition definition = this.hotkeys.get(id);

        if (definition != null)
        {
            definition.key = key;
            definition.mods = mods;
        }

        return null;
    }

    /**
     * 重置单个热键
     */
    public void reset(String id)
    {
        HotkeyDefinition definition = this.hotkeys.get(id);

        if (definition != null)
        {
            definition.reset();
        }
    }

    /**
     * 重置全部热键
     */
    public void resetAll()
    {
        for (HotkeyDefinition definition : this.hotkeys.values())
        {
            definition.reset();
        }
    }

    /**
     * 查找当前按键绑定的动作（按键分发用）
     */
    public HotkeyDefinition find(int keyCode, int glfwMods)
    {
        int mods = HotkeyDefinition.toMods(glfwMods);

        for (HotkeyDefinition definition : this.hotkeys.values())
        {
            if (definition.key == keyCode && definition.mods == mods)
            {
                return definition;
            }
        }

        return null;
    }
}
