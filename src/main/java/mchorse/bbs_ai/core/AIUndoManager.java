package mchorse.bbs_ai.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * AI 操作撤销/重做管理器
 *
 * <p>所有 AI 造成的可感知变更（生成影片 / 暂存动作 / IK 解算 / 模型应用）在执行后
 * {@link #record} 一条可逆操作；用户 Ctrl+Z / Ctrl+Y（AI 编辑器）或
 * {@code /bbs_ai undo|redo} 撤销重做。栈有上限，旧的自动淘汰。</p>
 *
 * <p>线程约定：record/undo/redo 都应在客户端主线程调用（AI 回调已切回主线程）。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class AIUndoManager
{
    /**
     * 撤销栈上限
     */
    private static final int LIMIT = 50;

    /**
     * 单例
     */
    private static final AIUndoManager INSTANCE = new AIUndoManager();

    /**
     * 撤销栈
     */
    private final Deque<Entry> undoStack = new ArrayDeque<>();

    /**
     * 重做栈
     */
    private final Deque<Entry> redoStack = new ArrayDeque<>();

    /**
     * 变更监听（UI 刷新按钮态）
     */
    private Consumer<Entry> changeListener;

    public static AIUndoManager get()
    {
        return INSTANCE;
    }

    private AIUndoManager()
    {}

    /**
     * 一条可逆操作
     */
    public static class Entry
    {
        /**
         * 操作类型标识（film/motion/ik/model）
         */
        public final String type;

        /**
         * 用户可读描述（如「生成影片 ai_editor_123」）
         */
        public final String description;

        /**
         * 撤销动作
         */
        public final Runnable undo;

        /**
         * 重做动作
         */
        public final Runnable redo;

        public Entry(String type, String description, Runnable undo, Runnable redo)
        {
            this.type = type;
            this.description = description;
            this.undo = undo;
            this.redo = redo;
        }
    }

    /**
     * 注册变更监听
     */
    public void setChangeListener(Consumer<Entry> listener)
    {
        this.changeListener = listener;
    }

    /**
     * 记录一条可逆操作（新操作清空重做栈）
     */
    public void record(String type, String description, Runnable undo, Runnable redo)
    {
        if (undo == null || redo == null)
        {
            return;
        }

        this.undoStack.push(new Entry(type, description, undo, redo));
        this.redoStack.clear();

        while (this.undoStack.size() > LIMIT)
        {
            this.undoStack.removeLast();
        }

        this.notifyChange();
    }

    /**
     * 撤销最近一次操作（返回被撤销的描述，null = 无可撤销）
     */
    public String undo()
    {
        Entry entry = this.undoStack.poll();

        if (entry == null)
        {
            return null;
        }

        try
        {
            entry.undo.run();
        }
        catch (Exception e)
        {
            System.err.println("[BBS AI] 撤销失败（" + entry.description + "）：" + e);
        }

        this.redoStack.push(entry);
        this.notifyChange();

        return entry.description;
    }

    /**
     * 重做最近一次被撤销的操作（返回描述，null = 无可重做）
     */
    public String redo()
    {
        Entry entry = this.redoStack.poll();

        if (entry == null)
        {
            return null;
        }

        try
        {
            entry.redo.run();
        }
        catch (Exception e)
        {
            System.err.println("[BBS AI] 重做失败（" + entry.description + "）：" + e);
        }

        this.undoStack.push(entry);
        this.notifyChange();

        return entry.description;
    }

    public boolean canUndo()
    {
        return !this.undoStack.isEmpty();
    }

    public boolean canRedo()
    {
        return !this.redoStack.isEmpty();
    }

    /**
     * 最近一条操作的描述（状态栏展示）
     */
    public String lastDescription()
    {
        Entry entry = this.undoStack.peek();

        return entry == null ? "" : entry.description;
    }

    private void notifyChange()
    {
        if (this.changeListener != null)
        {
            try
            {
                this.changeListener.accept(this.undoStack.peek());
            }
            catch (Exception e)
            {
                System.err.println("[BBS AI] 撤销监听回调失败（忽略）：" + e);
            }
        }
    }

    /**
     * 清空历史（世界切换等场景）
     */
    public void clear()
    {
        this.undoStack.clear();
        this.redoStack.clear();
        this.notifyChange();
    }
}
