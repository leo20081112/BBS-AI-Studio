package mchorse.bbs_ai.ui.editor;

import java.util.ArrayList;
import java.util.List;

import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * 思维链 / 工具调用日志（AI 编辑器右下区块）
 *
 * <p>展示 AI 工作过程中的每一步：请求发起 → 模型响应 → 代码块解析 → 动作执行结果，
 * 让用户看到「AI 正在做什么」。条目带状态色（进行中灰 / 成功绿 / 失败红），
 * 上限 40 条自动淘汰。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class AIToolCallLog extends UIElement
{
    /**
     * 日志条目上限
     */
    private static final int LIMIT = 40;

    /**
     * 单条日志
     */
    public static class Entry
    {
        public String label;
        public String result = "";
        public int state; /* 0 进行中 1 成功 2 失败 3 信息 */

        Entry(String label)
        {
            this.label = label;
        }
    }

    /**
     * 日志条目
     */
    private final List<Entry> entries = new ArrayList<>();

    /**
     * 内部滚动区
     */
    private final UIScrollView scroll;

    public AIToolCallLog()
    {
        this.scroll = UI.scrollView();
        this.scroll.relative(this).xy(0, 0).w(1F).h(1F);
        this.add(this.scroll);
    }

    /**
     * 开始一步工具调用（进行中状态），返回条目句柄
     */
    public Entry begin(String label)
    {
        Entry entry = new Entry(label);

        this.entries.add(entry);
        this.trim();
        this.rebuild();

        return entry;
    }

    /**
     * 追加一条即时信息（无进行中状态）
     */
    public void info(String label)
    {
        Entry entry = new Entry(label);

        entry.state = 3;
        this.entries.add(entry);
        this.trim();
        this.rebuild();
    }

    /**
     * 完成一步调用
     */
    public void complete(Entry entry, boolean ok, String result)
    {
        if (entry != null)
        {
            entry.state = ok ? 1 : 2;
            entry.result = result == null ? "" : result;
            this.rebuild();
        }
    }

    /**
     * 清空
     */
    public void clearLog()
    {
        this.entries.clear();
        this.rebuild();
    }

    private void trim()
    {
        while (this.entries.size() > LIMIT)
        {
            this.entries.remove(0);
        }
    }

    /**
     * 重建日志视图（Codex 风格卡片：标头 = 步骤名，正文 = 结果，自动换行不截断）
     */
    private void rebuild()
    {
        this.scroll.removeAll();

        for (Entry entry : this.entries)
        {
            String body = entry.result.isEmpty() ? "…" : entry.result;
            int accent = entry.state == 1 ? 0xFF5A8F63 : entry.state == 2 ? 0xFF8F3A3A : entry.state == 3 ? 0xFF3A5A8F : 0xFF3A4050;

            this.scroll.add(new AITextCard(
                entry.label,
                body,
                this.color(entry.state),
                0xFFAAB4C4,
                0x28000000,
                accent
            ));
        }

        /* 新日志在底部，滚动到底 */
        this.scroll.scroll.setScroll(Integer.MAX_VALUE);
    }

    private int color(int state)
    {
        if (state == 1)
        {
            return 0xff8fd694;
        }

        if (state == 2)
        {
            return 0xfff08d8d;
        }

        if (state == 3)
        {
            return 0xff9db8d6;
        }

        return Colors.GRAY;
    }

    @Override
    public void render(UIContext context)
    {
        /* 底色与右侧栏统一（现代深色卡片） */
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xD90D0F14);

        super.render(context);
    }
}
