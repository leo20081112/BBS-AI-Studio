package mchorse.bbs_ai.storyboard;

import mchorse.bbs_mod.camera.clips.overwrite.DollyClip;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.camera.clips.overwrite.KeyframeClip;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import java.util.HashMap;
import java.util.Map;

/**
 * 分镜 → Film 转换器
 *
 * <p>把 {@link StoryboardScript} 中的镜头 / 角色 / 转场转换为 BBS 原版
 * Film 数据结构【原版兼容】：
 * <ul>
 *   <li>CameraShot(dolly) → {@link DollyClip}：起点位置 + 朝向 + 位移距离 + 缓动</li>
 *   <li>CameraShot(pan/tilt/zoom/orbit) → {@link KeyframeClip}：位置 / 朝向 / FOV 关键帧</li>
 *   <li>CameraShot(静止) → {@link IdleClip}</li>
 *   <li>ActorShot → 对应 {@link Replay} 的 x/y/z/yaw 位置关键帧（同一 actor_id 复用同一 Replay）</li>
 *   <li>TransitionShot(fade/dissolve/wipe) → 相邻相机剪辑的淡入淡出包络；cut → 无操作</li>
 * </ul>
 * 转换结果先进入预烘焙预览系统，不直接写入正式影片（调用方负责）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class StoryboardToFilmConverter
{
    /**
     * 转换为全新 Film
     */
    public Film convert(StoryboardScript script)
    {
        Film film = new Film();

        this.convertInto(script, film);

        return film;
    }

    /**
     * 转换并写入已有 Film【原版兼容】
     *
     * @return 是否至少写入了一个剪辑 / 关键帧
     */
    public boolean convertInto(StoryboardScript script, Film film)
    {
        if (script == null || film == null || script.shots.isEmpty())
        {
            return false;
        }

        int cameraCursor = 0;
        Clip previousCameraClip = null;

        /* 角色 → Replay 映射与各自的时间游标 */
        Map<String, Replay> actors = new HashMap<>();
        Map<String, float[]> actorPositions = new HashMap<>();
        Map<String, Integer> actorCursors = new HashMap<>();

        for (Shot shot : script.shots)
        {
            if (shot instanceof CameraShot)
            {
                CameraShot camera = (CameraShot) shot;
                Clip clip = this.buildCameraClip(camera, cameraCursor);

                film.camera.addClip(clip);
                previousCameraClip = clip;
                cameraCursor += camera.durationTicks;
            }
            else if (shot instanceof ActorShot)
            {
                ActorShot actor = (ActorShot) shot;
                Replay replay = actors.computeIfAbsent(actor.actorId, (id) -> film.replays.addReplay());
                int cursor = actorCursors.getOrDefault(actor.actorId, 0);
                float[] from = actorPositions.getOrDefault(actor.actorId, new float[] {0.0F, 0.0F, 0.0F});

                this.applyActorShot(replay, from, actor, cursor);

                actorPositions.put(actor.actorId, new float[] {actor.targetPos[0], actor.targetPos[1], actor.targetPos[2]});
                actorCursors.put(actor.actorId, cursor + actor.durationTicks);
            }
            else if (shot instanceof TransitionShot)
            {
                TransitionShot transition = (TransitionShot) shot;

                /* 非硬切转场：给上一个相机剪辑添加淡入淡出包络【原版兼容】 */
                if (previousCameraClip != null && !TransitionShot.CUT.equals(transition.transitionType))
                {
                    previousCameraClip.envelope.enabled.set(true);
                    previousCameraClip.envelope.fadeIn.set(Math.max(1F, transition.durationTicks));
                    previousCameraClip.envelope.fadeOut.set(Math.max(1F, transition.durationTicks));
                }
            }
        }

        return !film.camera.get().isEmpty() || !film.replays.getList().isEmpty();
    }

    /**
     * 构建单个相机剪辑
     */
    private Clip buildCameraClip(CameraShot shot, int cursor)
    {
        IInterp interp = resolveEasing(shot.easing);

        switch (shot.shotType)
        {
            case CameraShot.DOLLY: return this.buildDollyClip(shot, cursor, interp);
            case CameraShot.PAN: return this.buildPanClip(shot, cursor, interp);
            case CameraShot.TILT: return this.buildTiltClip(shot, cursor, interp);
            case CameraShot.ZOOM: return this.buildZoomClip(shot, cursor, interp);
            case CameraShot.ORBIT: return this.buildOrbitClip(shot, cursor, interp);
            default: return this.buildIdleClip(shot, cursor);
        }
    }

    /**
     * 推拉镜头：起点 + 朝向 + 位移距离【原版兼容】
     */
    private Clip buildDollyClip(CameraShot shot, int cursor, IInterp interp)
    {
        DollyClip clip = new DollyClip();

        float dx = shot.endPos[0] - shot.startPos[0];
        float dy = shot.endPos[1] - shot.startPos[1];
        float dz = shot.endPos[2] - shot.startPos[2];
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        clip.position.get().point.set(shot.startPos[0], shot.startPos[1], shot.startPos[2]);

        float yaw = shot.startYaw != null ? shot.startYaw : yawTo(dx, dz);
        float pitch = shot.startPitch != null ? shot.startPitch : pitchTo(dy, dx, dz);

        clip.yaw.set(yaw);
        clip.pitch.set(pitch);
        clip.distance.set(Math.max(0.1F, distance));
        clip.interp.setInterp(interp);
        clip.tick.set(cursor);
        clip.duration.set(Math.max(1, shot.durationTicks));
        clip.title.set("AI: dolly");

        return clip;
    }

    /**
     * 水平摇镜：位置固定，yaw 旋转 90 度【原版兼容】
     */
    private Clip buildPanClip(CameraShot shot, int cursor, IInterp interp)
    {
        KeyframeClip clip = this.baseKeyframeClip(shot, cursor);
        float dx = shot.endPos[0] - shot.startPos[0];
        float dz = shot.endPos[2] - shot.startPos[2];
        float yaw = shot.startYaw != null ? shot.startYaw : yawTo(dx, dz);

        clip.yaw.insert(0, (double) yaw);
        clip.yaw.insert(shot.durationTicks, (double) (yaw + 90.0F));
        applyInterp(clip.yaw, interp);

        return clip;
    }

    /**
     * 俯仰摇镜：位置固定，pitch 上抬 45 度【原版兼容】
     */
    private Clip buildTiltClip(CameraShot shot, int cursor, IInterp interp)
    {
        KeyframeClip clip = this.baseKeyframeClip(shot, cursor);
        float dx = shot.endPos[0] - shot.startPos[0];
        float dy = shot.endPos[1] - shot.startPos[1];
        float dz = shot.endPos[2] - shot.startPos[2];
        float pitch = shot.startPitch != null ? shot.startPitch : pitchTo(dy, dx, dz);

        clip.pitch.insert(0, (double) pitch);
        clip.pitch.insert(shot.durationTicks, (double) Math.max(-89.0F, pitch - 45.0F));
        applyInterp(clip.pitch, interp);

        return clip;
    }

    /**
     * 变焦：位置固定，FOV 从 70 收窄到 35【原版兼容】
     */
    private Clip buildZoomClip(CameraShot shot, int cursor, IInterp interp)
    {
        KeyframeClip clip = this.baseKeyframeClip(shot, cursor);

        clip.fov.insert(0, 70.0D);
        clip.fov.insert(shot.durationTicks, 35.0D);
        applyInterp(clip.fov, interp);

        return clip;
    }

    /**
     * 环绕：围绕 endPos（圆心）一周，半径为 start/end 距离【原版兼容】
     */
    private Clip buildOrbitClip(CameraShot shot, int cursor, IInterp interp)
    {
        KeyframeClip clip = this.baseKeyframeClip(shot, cursor);

        float cx = shot.endPos[0];
        float cy = shot.endPos[1];
        float cz = shot.endPos[2];
        float dx = shot.startPos[0] - cx;
        float dz = shot.startPos[2] - cz;
        float radius = Math.max(1.0F, (float) Math.sqrt(dx * dx + dz * dz));

        /* 四分点采样圆周 + 面向圆心的 yaw */
        int steps = Math.max(4, Math.min(8, shot.durationTicks / 10));

        for (int i = 0; i <= steps; i++)
        {
            double angle = 2.0 * Math.PI * i / steps;
            double px = cx + Math.sin(angle) * radius;
            double pz = cz - Math.cos(angle) * radius;
            double tick = shot.durationTicks * (double) i / steps;

            clip.x.insert((float) tick, px);
            clip.z.insert((float) tick, pz);
            clip.y.insert((float) tick, (double) cy);

            /* 相机始终看向圆心：yaw = atan2(-dx, dz) */
            float lookX = (float) (cx - px);
            float lookZ = (float) (cz - pz);

            clip.yaw.insert((float) tick, (double) yawTo(lookX, lookZ));
        }

        applyInterp(clip.x, interp);
        applyInterp(clip.y, interp);
        applyInterp(clip.z, interp);
        applyInterp(clip.yaw, interp);

        return clip;
    }

    /**
     * 静止镜头【原版兼容】
     */
    private Clip buildIdleClip(CameraShot shot, int cursor)
    {
        IdleClip clip = new IdleClip();

        clip.position.get().point.set(shot.startPos[0], shot.startPos[1], shot.startPos[2]);
        clip.position.get().angle.yaw = shot.startYaw != null ? shot.startYaw : yawTo(shot.endPos[0] - shot.startPos[0], shot.endPos[2] - shot.startPos[2]);
        clip.position.get().angle.pitch = shot.startPitch != null ? shot.startPitch : pitchTo(shot.endPos[1] - shot.startPos[1], shot.endPos[0] - shot.startPos[0], shot.endPos[2] - shot.startPos[2]);
        clip.tick.set(cursor);
        clip.duration.set(Math.max(1, shot.durationTicks));
        clip.title.set("AI: idle");

        return clip;
    }

    /**
     * 关键帧剪辑公共部分：固定相机位置【原版兼容】
     */
    private KeyframeClip baseKeyframeClip(CameraShot shot, int cursor)
    {
        KeyframeClip clip = new KeyframeClip();

        clip.x.insert(0, (double) shot.startPos[0]);
        clip.y.insert(0, (double) shot.startPos[1]);
        clip.z.insert(0, (double) shot.startPos[2]);
        clip.tick.set(cursor);
        clip.duration.set(Math.max(1, shot.durationTicks));
        clip.title.set("AI: " + shot.shotType);

        return clip;
    }

    /**
     * 应用角色 Shot：写入位置与朝向关键帧【原版兼容】
     */
    private void applyActorShot(Replay replay, float[] from, ActorShot shot, int cursor)
    {
        int duration = Math.max(1, shot.durationTicks);
        KeyframeChannel<Double> x = replay.keyframes.x;
        KeyframeChannel<Double> y = replay.keyframes.y;
        KeyframeChannel<Double> z = replay.keyframes.z;
        KeyframeChannel<Double> yaw = replay.keyframes.yaw;

        x.insert(cursor, (double) from[0]);
        y.insert(cursor, (double) from[1]);
        z.insert(cursor, (double) from[2]);
        x.insert(cursor + duration, (double) shot.targetPos[0]);
        y.insert(cursor + duration, (double) shot.targetPos[1]);
        z.insert(cursor + duration, (double) shot.targetPos[2]);

        /* 面向移动方向 */
        float dx = shot.targetPos[0] - from[0];
        float dz = shot.targetPos[2] - from[2];

        if (Math.abs(dx) > 0.01F || Math.abs(dz) > 0.01F)
        {
            float facing = yawTo(dx, dz);

            yaw.insert(cursor, (double) facing);
            yaw.insert(cursor + duration, (double) facing);
        }
    }

    /**
     * 给关键帧通道内的所有关键帧应用插值曲线
     */
    private static void applyInterp(KeyframeChannel<Double> channel, IInterp interp)
    {
        for (Keyframe<Double> keyframe : channel.getKeyframes())
        {
            keyframe.getInterpolation().setInterp(interp);
        }
    }

    /**
     * 缓动名称 → BBS 插值器
     */
    static IInterp resolveEasing(String easing)
    {
        if (easing == null)
        {
            return Interpolations.LINEAR;
        }

        switch (easing)
        {
            case "linear": return Interpolations.LINEAR;
            case "ease_in": return Interpolations.QUAD_IN;
            case "ease_out": return Interpolations.QUAD_OUT;
            case "ease_in_out":
            default: return Interpolations.SINE_INOUT;
        }
    }

    /**
     * 位移方向 → Minecraft yaw（度）。yaw 0 = +Z，正方向顺时针
     */
    static float yawTo(float dx, float dz)
    {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * 位移方向 → Minecraft pitch（度），向下为正
     */
    static float pitchTo(float dy, float dx, float dz)
    {
        float horizontal = (float) Math.sqrt(dx * dx + dz * dz);

        return (float) Math.toDegrees(Math.atan2(-dy, Math.max(horizontal, 0.0001F)));
    }
}
