package mchorse.bbs_ai.storyboard;

import com.google.gson.JsonObject;

/**
 * 转场 Shot：镜头切换方式描述
 *
 * <p>支持转场类型：cut（硬切）、fade（淡入淡出）、dissolve（叠化）、
 * wipe（擦除）。受 BBS 原版能力限制，非 cut 转场以相邻镜头的
 * 淡入淡出包络近似实现【原版兼容】。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class TransitionShot extends Shot
{
    /**
     * 转场类型常量
     */
    public static final String CUT = "cut";
    public static final String FADE = "fade";
    public static final String DISSOLVE = "dissolve";
    public static final String WIPE = "wipe";

    /**
     * 转场类型
     */
    public String transitionType = CUT;

    /**
     * 转场持续时间（tick）
     */
    public int durationTicks = 10;

    @Override
    public String getType()
    {
        return "transition";
    }

    @Override
    public JsonObject toJson()
    {
        JsonObject object = super.toJson();

        object.addProperty("transition_type", this.transitionType);
        object.addProperty("duration_ticks", this.durationTicks);

        return object;
    }

    /**
     * 从模型输出的 JSON 解析
     */
    public static TransitionShot fromJson(JsonObject object)
    {
        TransitionShot shot = new TransitionShot();

        shot.transitionType = CameraShot.getString(object, "transition_type", CUT);
        shot.durationTicks = Math.max(0, CameraShot.getInt(object, "duration_ticks", 10));

        return shot;
    }
}
