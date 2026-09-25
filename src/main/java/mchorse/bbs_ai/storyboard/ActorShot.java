package mchorse.bbs_ai.storyboard;

import com.google.gson.JsonObject;

/**
 * 角色 Shot：角色动作描述
 *
 * <p>支持动作类型：walk（走）、run（跑）、jump（跳）、wave（挥手）、
 * idle（待机）、sit（坐）、sleep（躺）。目标位置为 Minecraft 方块坐标。
 * 转换为 Film 时生成角色的位置 / 朝向关键帧【原版兼容】。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ActorShot extends Shot
{
    /**
     * 动作类型常量
     */
    public static final String WALK = "walk";
    public static final String RUN = "run";
    public static final String JUMP = "jump";
    public static final String WAVE = "wave";
    public static final String IDLE = "idle";
    public static final String SIT = "sit";
    public static final String SLEEP = "sleep";

    /**
     * 角色 ID（如 actor_1；同一 ID 的 Shot 复用同一个 Replay）
     */
    public String actorId = "actor_1";

    /**
     * 动作类型
     */
    public String actionType = WALK;

    /**
     * 目标位置 [x, y, z]（角色移动终点或原地动作位置）
     */
    public float[] targetPos = new float[] {0.0F, 0.0F, 0.0F};

    /**
     * 持续时间（tick）
     */
    public int durationTicks = 40;

    /**
     * 是否循环动作
     */
    public boolean loop = false;

    @Override
    public String getType()
    {
        return "actor";
    }

    @Override
    public JsonObject toJson()
    {
        JsonObject object = super.toJson();

        object.addProperty("actor_id", this.actorId);
        object.addProperty("action_type", this.actionType);
        object.add("target_pos", CameraShot.vector(this.targetPos));
        object.addProperty("duration_ticks", this.durationTicks);
        object.addProperty("loop", this.loop);

        return object;
    }

    /**
     * 从模型输出的 JSON 解析
     */
    public static ActorShot fromJson(JsonObject object)
    {
        ActorShot shot = new ActorShot();

        shot.actorId = CameraShot.getString(object, "actor_id", "actor_1");
        shot.actionType = CameraShot.getString(object, "action_type", WALK);
        shot.targetPos = CameraShot.getVector(object, "target_pos", shot.targetPos);
        shot.durationTicks = Math.max(1, CameraShot.getInt(object, "duration_ticks", 40));
        shot.loop = CameraShot.getBool(object, "loop", false);

        return shot;
    }
}
