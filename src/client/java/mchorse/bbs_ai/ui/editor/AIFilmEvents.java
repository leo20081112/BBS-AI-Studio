package mchorse.bbs_ai.ui.editor;

import java.util.ArrayList;
import java.util.List;

import mchorse.bbs_mod.api.client.events.FilmEditEvents;
import mchorse.bbs_mod.api.client.events.TimelineEvents;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * AI 影片事件接入（bbs-fs 2.7 新体系 M2）
 *
 * <p>双时间轴联动：
 * <ul>
 *   <li>{@code TimelineEvents.OVERLAY}：把 AI 分镜边界（合并分镜后各镜头的起始 tick）
 *       渲染成黄色竖线叠加在影片编辑器原生时间轴上（相机剪辑与角色关键帧两条轨道共享）</li>
 *   <li>{@code FilmEditEvents.CHANGED}：影片被编辑（含撤销/重做）时通知 AI 编辑器刷新，
 *       经由 {@link #setExternalEditListener} 注入的回调（在主线程）</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class AIFilmEvents
{
    /**
     * AI 合并分镜后的镜头起始 tick 列表（升序；OVERLAY 渲染用）
     */
    public static final List<Integer> shotBoundaries = new ArrayList<>();

    /**
     * AI 分镜标签
     */
    public static String shotLabel = "AI";

    /**
     * 外部编辑回调（AI 编辑器面板构造时注入；主线程调用）
     */
    private static Runnable externalEditListener;

    /**
     * 注册全部时间轴/编辑事件（客户端初始化时调用一次）
     */
    public static void register()
    {
        TimelineEvents.OVERLAY.register(AIFilmEvents::renderOverlay);

        FilmEditEvents.CHANGED.register((film, values, cause) ->
        {
            if (cause == FilmEditEvents.Cause.EDIT && externalEditListener != null)
            {
                externalEditListener.run();
            }
        });
    }

    /**
     * 注入外部编辑回调（AI 编辑器面板用）
     */
    public static void setExternalEditListener(Runnable listener)
    {
        externalEditListener = listener;
    }

    /**
     * 记录 AI 分镜边界（由调用方传入合并后影片的各相机剪辑起始 tick）
     */
    public static void setShotBoundaries(List<Integer> ticks)
    {
        shotBoundaries.clear();
        shotBoundaries.addAll(ticks);
    }

    /**
     * 时间轴覆盖层渲染：AI 分镜边界竖线 + 顶部标签
     */
    private static void renderOverlay(mchorse.bbs_mod.film.Film film,
        mchorse.bbs_mod.ui.framework.UIContext context, Area area,
        java.util.function.DoubleToIntFunction toX)
    {
        if (shotBoundaries.isEmpty())
        {
            return;
        }

        for (int tick : shotBoundaries)
        {
            int x = toX.applyAsInt(tick);

            if (x < area.x || x >= area.ex())
            {
                continue;
            }

            context.batcher.box(x - 1, area.y + 2, x + 1, area.ey() - 2, 0xFFE0B040);
            context.batcher.text(shotLabel, x + 3, area.y + 2, 0xFFE0B040, true);
        }
    }
}
