package mchorse.bbs_ai.ui.editor;

import java.util.function.Supplier;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * 影片时间轴（AI 编辑器左下）
 *
 * <p>可视化当前打开影片的相机剪辑轨道与角色轨道：横轴为 tick，
 * 相机剪辑画成条块（可点击选中），角色轨道显示角色数量带；
 * AI 生成的分镜写入影片后此处自动同步。无影片时显示引导占位。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class AIFilmTimeline extends UIElement
{
    /**
     * 当前影片提供者（影片面板 getData）
     */
    private final Supplier<Film> film;

    /**
     * 选中剪辑回调（参数为剪辑序号，-1 = 取消）
     */
    private final java.util.function.Consumer<Integer> select;

    /**
     * 选中剪辑序号
     */
    private int selected = -1;

    /**
     * 拖拽中
     */
    private boolean dragging;

    public AIFilmTimeline(Supplier<Film> film, java.util.function.Consumer<Integer> select)
    {
        this.film = film;
        this.select = select;
    }

    public int getSelected()
    {
        return this.selected;
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (this.area.isInside(context))
        {
            this.dragging = true;
            this.pick(context.mouseX);

            return true;
        }

        return false;
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        this.dragging = false;

        return false;
    }

    @Override
    public void render(UIContext context)
    {
        Film film = this.film.get();

        /* 底色 */
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xE60D0F14);

        int x1 = this.area.x + 4;
        int x2 = this.area.ex() - 4;

        if (film == null)
        {
            context.batcher.text(L10n.lang("bbs_ai.editor.timeline.film_empty").get(), x1 + 4, this.area.y + 4, Colors.GRAY, true);

            return;
        }

        java.util.List<Clip> clips = film.camera.getClips(Clip.class);

        int span = 0;

        for (Clip clip : clips)
        {
            span = Math.max(span, clip.tick.get() + clip.duration.get());
        }

        span = Math.max(span, 1);

        /* ---- 相机剪辑轨道 ---- */
        int trackY = this.area.y + 14;

        context.batcher.text(L10n.lang("bbs_ai.editor.timeline.camera").get(), x1 + 2, this.area.y + 3, 0xFF8FA3C8, true);
        context.batcher.box(x1, trackY + 8, x2, trackY + 9, 0xFF2A2E3A);

        for (int i = 0; i < clips.size(); i++)
        {
            Clip clip = clips.get(i);
            int cx1 = x1 + (int) ((x2 - x1) * (clip.tick.get() / (float) span));
            int cx2 = x1 + (int) ((x2 - x1) * ((clip.tick.get() + clip.duration.get()) / (float) span));

            cx2 = Math.max(cx2, cx1 + 3);

            int color = i == this.selected ? 0xFF7CC98B : 0xFF4C6EF5;

            context.batcher.box(cx1, trackY, cx2, trackY + 16, color);
        }

        /* ---- 角色轨道 ---- */
        int actorY = this.area.y + 40;
        int actors = film.replays.getList().size();

        context.batcher.text(L10n.lang("bbs_ai.editor.timeline.actors").get(), x1 + 2, actorY - 9, 0xFF8FA3C8, true);
        context.batcher.box(x1, actorY + 8, x2, actorY + 9, 0xFF2A2E3A);

        if (actors > 0)
        {
            int ax2 = x1 + (int) ((x2 - x1) * Math.min(1F, (actors * 60) / (float) span));

            context.batcher.box(x1, actorY, ax2, actorY + 16, 0xFF8A5CF6);
            context.batcher.text(actors + " 角", x1 + 6, actorY + 3, Colors.WHITE, true);
        }

        /* ---- 信息行 ---- */
        String info = clips.size() + " 相机剪辑 · " + actors + " 角色 · " + span + " tick"
            + (this.selected >= 0 && this.selected < clips.size() ? " · 已选 #" + this.selected : "");

        context.batcher.text(info, x1 + 4, this.area.ey() - 12, Colors.GRAY, true);

        if (this.dragging)
        {
            this.pick(context.mouseX);
        }

        super.render(context);
    }

    /**
     * 按鼠标 X 选择命中时间点上的剪辑
     */
    private void pick(int mouseX)
    {
        Film film = this.film.get();

        if (film == null)
        {
            return;
        }

        java.util.List<Clip> clips = film.camera.getClips(Clip.class);

        if (clips.isEmpty())
        {
            return;
        }

        int span = 0;

        for (Clip clip : clips)
        {
            span = Math.max(span, clip.tick.get() + clip.duration.get());
        }

        span = Math.max(span, 1);

        int x1 = this.area.x + 4;
        int x2 = this.area.ex() - 4;
        int tick = (int) ((mouseX - x1) / (float) Math.max(1, x2 - x1) * span);
        int best = -1;
        int bestDist = Integer.MAX_VALUE;

        for (int i = 0; i < clips.size(); i++)
        {
            Clip clip = clips.get(i);

            if (tick >= clip.tick.get() && tick <= clip.tick.get() + clip.duration.get())
            {
                best = i;

                break;
            }

            int dist = Math.min(Math.abs(tick - clip.tick.get()), Math.abs(tick - clip.tick.get() - clip.duration.get()));

            if (dist < bestDist)
            {
                bestDist = dist;
                best = i;
            }
        }

        if (best != this.selected)
        {
            this.selected = best;

            if (this.select != null)
            {
                this.select.accept(this.selected);
            }
        }
    }
}
