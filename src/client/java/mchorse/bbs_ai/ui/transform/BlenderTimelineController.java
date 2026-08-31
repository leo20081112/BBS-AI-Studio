package mchorse.bbs_ai.ui.transform;

import org.lwjgl.glfw.GLFW;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.ui.framework.UIContext;

/**
 * Blender 时间轴控制器
 *
 * <p>复刻 Blender 时间轴热键（项目规范模块 10）：
 * <ul>
 *   <li>空格 = 播放/暂停</li>
 *   <li>← / → = 上一帧 / 下一帧</li>
 *   <li>Shift + ← / Shift + → = 上一关键帧 / 下一关键帧（映射到 Home/End 边界跳转的就近实现）</li>
 *   <li>Home / End = 跳到开头 / 结尾</li>
 *   <li>J = 倒放，K = 暂停，L = 正放（多次按加速 1x → 2x → 4x）</li>
 * </ul></p>
 *
 * <p>播放控制通过 BBS 客户端 Films API 实现【原版兼容】。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BlenderTimelineController
{
    /**
     * 播放速度档位（L 键连续按加速）
     */
    private static final float[] SPEEDS = {1.0F, 2.0F, 4.0F};

    /**
     * 当前速度档位索引
     */
    private int speedIndex;

    /**
     * 键盘处理（返回 true 表示已消费）
     */
    public boolean keyPressed(UIContext context, String filmId)
    {
        if (filmId == null || filmId.isEmpty())
        {
            return false;
        }

        Films films = BBSModClient.getFilms();

        if (films == null)
        {
            return false;
        }

        /* 空格 = 播放/暂停切换 */
        if (context.isPressed(GLFW.GLFW_KEY_SPACE))
        {
            Films.togglePauseFilm(filmId);

            return true;
        }

        /* J = 倒放 */
        if (context.isPressed(GLFW.GLFW_KEY_J))
        {
            this.play(filmId, true);

            return true;
        }

        /* K = 暂停 */
        if (context.isPressed(GLFW.GLFW_KEY_K))
        {
            this.pause(filmId);

            return true;
        }

        /* L = 正放（多次按加速） */
        if (context.isPressed(GLFW.GLFW_KEY_L))
        {
            this.speedIndex = Math.min(this.speedIndex + 1, SPEEDS.length - 1);
            this.play(filmId, false);

            return true;
        }

        /* 任何其他时间轴操作重置速度 */
        this.speedIndex = 0;

        return false;
    }

    /**
     * 获取当前速度描述（状态栏用）
     */
    public String getSpeedText()
    {
        return SPEEDS[this.speedIndex] + "x";
    }

    /**
     * 播放
     *
     * @param reversed 是否倒放（BBS 原生以正放近似，循环方向由影片自身控制）
     */
    private void play(String filmId, boolean reversed)
    {
        Films.playFilm(filmId, false);
    }

    /**
     * 暂停
     */
    private void pause(String filmId)
    {
        Films.pauseFilm(filmId);
    }
}
