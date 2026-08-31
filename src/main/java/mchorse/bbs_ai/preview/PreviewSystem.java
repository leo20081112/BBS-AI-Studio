package mchorse.bbs_ai.preview;

import mchorse.bbs_ai.format.MotionFrame;
import mchorse.bbs_ai.import_manager.SourceType;
import mchorse.bbs_ai.integration.event.PreviewModeEnterEvent;
import mchorse.bbs_ai.integration.event.PreviewModeExitEvent;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.PoseTransform;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 预烘焙预览系统（单例总控）
 *
 * <p>工作流（见项目规范）：
 * <pre>
 * AI生成 / IK调整 / 分镜生成 / 外部导入
 *   → {@link #stage(PreviewContext, List)}（写入 {@link PrebakeCache} 内存缓存）
 *   → 时间轴显示虚线预览轨道 + 3D 视口预览渲染（客户端 PreviewRenderer）
 *   → 用户调整参数 → 防抖刷新（客户端职责）
 *   → BakeConfirmationDialog（客户端）
 *   → 确认 → {@link #bake(BakeMode)} 正式写入 Film【原版兼容】
 *   → 取消 → {@link #discard()} 清除缓存恢复原状
 * </pre></p>
 *
 * <p>烘焙写入通过可插拔的 {@link BakeTarget} 完成：客户端初始化时通过
 * {@link #setBakeTarget(BakeTarget)} 注入「写入当前编辑影片」的实现；
 * 未注入时回退到 FilmManager 离线写入。</p>
 *
 * <p>线程契约：{@code stage} 在后台线程调用；{@code bake/discard} 在主线程调用；
 * 监听器回调在触发线程，UI 自行调度。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class PreviewSystem
{
    /**
     * 骨骼通道前缀【原版兼容】（与 PerLimbService.POSE_BONES 一致）
     */
    public static final String POSE_BONES_PREFIX = "pose.bones.";

    /**
     * INSERT_BLEND 模式的边界混合宽度（tick）
     */
    public static final float BLEND_RANGE = 5.0F;

    /**
     * 单例
     */
    private static PreviewSystem instance;

    /**
     * 内存缓存
     */
    private final PrebakeCache cache = new PrebakeCache();

    /**
     * 可插拔烘焙目标（客户端注入）
     */
    private volatile BakeTarget bakeTarget;

    /**
     * 是否处于预览模式
     */
    private volatile boolean previewMode;

    /**
     * 预览数据变更监听器（渲染层 / 时间轴订阅）
     */
    private final List<Consumer<PreviewSystem>> listeners = new CopyOnWriteArrayList<>();

    /**
     * 初始化
     */
    public static synchronized void initialize()
    {
        if (instance == null)
        {
            instance = new PreviewSystem();
        }
    }

    /**
     * 获取单例
     */
    public static PreviewSystem get()
    {
        if (instance == null)
        {
            initialize();
        }

        return instance;
    }

    /**
     * 注入烘焙目标（客户端初始化时调用一次）
     */
    public void setBakeTarget(BakeTarget target)
    {
        this.bakeTarget = target;
    }

    /**
     * 订阅预览变更
     */
    public void addListener(Consumer<PreviewSystem> listener)
    {
        if (listener != null)
        {
            this.listeners.add(listener);
        }
    }

    /**
     * 移除监听
     */
    public void removeListener(Consumer<PreviewSystem> listener)
    {
        this.listeners.remove(listener);
    }

    /**
     * 是否处于预览模式
     */
    public boolean isPreviewMode()
    {
        return this.previewMode;
    }

    /**
     * 内存缓存（只读使用）
     */
    public PrebakeCache getCache()
    {
        return this.cache;
    }

    /**
     * 暂存一份预览数据（后台线程调用）
     *
     * @param context 预览上下文（目标影片 / 角色 / 表单 / 来源 / 时间范围）
     * @param frames  临时关键帧
     * @return 预览轨道
     */
    public PreviewTrack stage(PreviewContext context, List<MotionFrame> frames)
    {
        if (context == null || frames == null || frames.isEmpty())
        {
            return null;
        }

        PreviewTrack track = new PreviewTrack(context.source.name().toLowerCase(), frames);

        this.cache.put(context, track);

        if (!this.previewMode)
        {
            this.previewMode = true;

            /* 进入预览模式事件（供 addon 扩展） */
            BBSMod.events.post(new PreviewModeEnterEvent(context));
        }

        this.notifyListeners();

        return track;
    }

    /**
     * 便捷重载：根据数据自动推导时间范围
     */
    public PreviewTrack stage(String filmId, String replayId, String formPath, SourceType source, List<MotionFrame> frames)
    {
        if (frames == null || frames.isEmpty())
        {
            return null;
        }

        int start = frames.get(0).tick;
        int end = frames.get(frames.size() - 1).tick;

        return this.stage(new PreviewContext(filmId, replayId, formPath, source, start, end), frames);
    }

    /**
     * 放弃全部预览（主线程调用）：清除缓存，恢复原状
     */
    public void discard()
    {
        boolean wasPreviewing = this.previewMode;

        this.cache.discardAll();
        this.previewMode = false;

        if (wasPreviewing)
        {
            BBSMod.events.post(new PreviewModeExitEvent(false));
        }

        this.notifyListeners();
    }

    /**
     * 确认烘焙：把预览数据正式写入 Film【原版兼容】（主线程调用）
     *
     * @param mode 烘焙模式（覆盖现有 / 插入混合）
     * @return 成功写入的关键帧数量；取消或无预览时为 0
     */
    public int bake(BakeMode mode)
    {
        if (mode == BakeMode.CANCEL || mode == null)
        {
            this.discard();

            return 0;
        }

        int written = 0;
        BakeTarget target = this.bakeTarget;

        for (String key : new ArrayList<>(this.cache.keys()))
        {
            PreviewTrack track = this.cache.get(key);
            PreviewContext context = this.cache.getContext(key);

            if (track == null || context == null)
            {
                continue;
            }

            if (target != null)
            {
                written += target.applyKeyframes(context, track, mode);
            }
            else
            {
                written += new ManagerBakeTarget().applyKeyframes(context, track, mode);
            }
        }

        boolean wasPreviewing = this.previewMode;

        this.cache.discardAll();
        this.previewMode = false;

        if (wasPreviewing)
        {
            BBSMod.events.post(new PreviewModeExitEvent(true));
        }

        this.notifyListeners();

        return written;
    }

    /**
     * 统计冲突：现有关键帧与预览时间范围重叠的通道数与帧数
     *
     * @return int[]{冲突通道数, 冲突帧数}
     */
    public int[] detectConflicts(PreviewContext context)
    {
        Film film = new ManagerBakeTarget().resolveFilm(context.filmId);

        if (film == null)
        {
            return new int[] {0, 0};
        }

        Replay replay = new ManagerBakeTarget().resolveReplay(film, context.replayId);

        if (replay == null)
        {
            return new int[] {0, 0};
        }

        Set<String> conflictedChannels = new HashSet<>();
        int conflictedFrames = 0;

        for (KeyframeChannel<?> channel : new ArrayList<>(replay.properties.properties.values()))
        {
            for (Keyframe<?> keyframe : channel.getKeyframes())
            {
                float tick = keyframe.getTick();

                if (tick >= context.tickStart && tick <= context.tickEnd)
                {
                    conflictedChannels.add(channel.getId());
                    conflictedFrames++;
                }
            }
        }

        return new int[] {conflictedChannels.size(), conflictedFrames};
    }

    /**
     * 触发监听器
     */
    private void notifyListeners()
    {
        for (Consumer<PreviewSystem> listener : this.listeners)
        {
            try
            {
                listener.accept(this);
            }
            catch (Exception e)
            {
                System.err.println("[BBS AI] 预览监听器异常：" + e.getMessage());
            }
        }
    }

    /**
     * 默认烘焙目标：通过 FilmManager 离线写入（服务端 / 无头流程）【原版兼容】
     */
    public static class ManagerBakeTarget implements BakeTarget
    {
        @Override
        public int applyKeyframes(PreviewContext context, PreviewTrack track, BakeMode mode)
        {
            Film film = this.resolveFilm(context.filmId);

            if (film == null)
            {
                System.err.println("[BBS AI] 烘焙失败：找不到影片 " + context.filmId);

                return 0;
            }

            Replay replay = this.resolveReplay(film, context.replayId);

            if (replay == null)
            {
                System.err.println("[BBS AI] 烘焙失败：找不到角色 " + context.replayId);

                return 0;
            }

            int written = this.writeTrack(film, replay, context, track, mode);

            /* 持久化影片数据【原版兼容】 */
            if (written > 0)
            {
                BBSMod.getFilms().save(context.filmId, (mchorse.bbs_mod.data.types.MapType) film.toData());
            }

            return written;
        }

        /**
         * 把轨道数据写入 Replay 的骨骼姿态通道【原版兼容】
         */
        int writeTrack(Film film, Replay replay, PreviewContext context, PreviewTrack track, BakeMode mode)
        {
            Form form = replay.form.get();

            if (form == null)
            {
                System.err.println("[BBS AI] 烘焙跳过：角色没有表单");

                return 0;
            }

            int written = 0;

            for (String bone : track.getAffectedBones())
            {
                KeyframeChannel channel = replay.properties.getOrCreate(form, POSE_BONES_PREFIX + bone);

                if (channel == null)
                {
                    continue;
                }

                /* 覆盖模式：先清除时间范围内的现有关键帧 */
                if (mode == BakeMode.OVERWRITE)
                {
                    removeKeyframesInRange(channel, context.tickStart, context.tickEnd);
                }

                for (MotionFrame frame : track.getFrames())
                {
                    if (!frame.bones.containsKey(bone))
                    {
                        continue;
                    }

                    float[] rotation = frame.bones.get(bone).rotation;

                    /* 混合模式：边界 ±BLEND_RANGE 内与现有取值线性混合 */
                    if (mode == BakeMode.INSERT_BLEND)
                    {
                        rotation = blendBoundary(channel, frame.tick, context, rotation);
                    }

                    PoseTransform poseTransform = new PoseTransform();

                    poseTransform.identity();
                    poseTransform.rotate.set(MathUtils.toRad(rotation[0]), MathUtils.toRad(rotation[1]), MathUtils.toRad(rotation[2]));

                    channel.insert(frame.tick, poseTransform);
                    written++;
                }
            }

            return written;
        }

        /**
         * 移除通道中 [start, end] 范围内的全部关键帧
         */
        static void removeKeyframesInRange(KeyframeChannel<?> channel, int start, int end)
        {
            List<Integer> toRemove = new ArrayList<>();
            List<? extends Keyframe<?>> keyframes = channel.getKeyframes();

            for (int i = 0; i < keyframes.size(); i++)
            {
                float tick = keyframes.get(i).getTick();

                if (tick >= start && tick <= end)
                {
                    toRemove.add(i);
                }
            }

            /* 从后往前删除，避免索引移位 */
            for (int i = toRemove.size() - 1; i >= 0; i--)
            {
                channel.remove(toRemove.get(i));
            }
        }

        /**
         * 插入混合：在时间范围边界 ±BLEND_RANGE 内与现有通道取值做线性混合
         * （现有姿态为弧度，换算为度后混合）
         */
        static float[] blendBoundary(KeyframeChannel<?> channel, int tick, PreviewContext context, float[] rotation)
        {
            float[] result = new float[] {rotation[0], rotation[1], rotation[2]};

            float blend = 0.0F;

            if (tick >= context.tickStart && tick <= context.tickStart + BLEND_RANGE)
            {
                blend = 1.0F - (tick - context.tickStart) / BLEND_RANGE;
            }
            else if (tick >= context.tickEnd - BLEND_RANGE && tick <= context.tickEnd)
            {
                blend = 1.0F - (context.tickEnd - tick) / BLEND_RANGE;
            }

            if (blend > 0.001F && !channel.isEmpty())
            {
                Object existing = channel.interpolate(tick);

                if (existing instanceof PoseTransform pose)
                {
                    result[0] = rotation[0] * (1.0F - blend) + MathUtils.toDeg(pose.rotate.x) * blend;
                    result[1] = rotation[1] * (1.0F - blend) + MathUtils.toDeg(pose.rotate.y) * blend;
                    result[2] = rotation[2] * (1.0F - blend) + MathUtils.toDeg(pose.rotate.z) * blend;
                }
            }

            return result;
        }

        /**
         * 解析影片【原版兼容】
         */
        Film resolveFilm(String filmId)
        {
            if (filmId == null || filmId.isEmpty())
            {
                return null;
            }

            try
            {
                return BBSMod.getFilms().load(filmId);
            }
            catch (Exception e)
            {
                System.err.println("[BBS AI] 加载影片失败：" + filmId + "，" + e.getMessage());

                return null;
            }
        }

        /**
         * 按 ID 解析角色（找不到时回退第一个）【原版兼容】
         */
        Replay resolveReplay(Film film, String replayId)
        {
            for (Replay replay : film.replays.getList())
            {
                if (replay.getId().equals(replayId))
                {
                    return replay;
                }
            }

            return film.replays.getList().isEmpty() ? null : film.replays.getList().get(0);
        }
    }
}
