package mchorse.bbs_ai.ui.hotkey;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.lwjgl.glfw.GLFW;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * 热键自定义设置面板
 *
 * <p>功能（项目规范模块 11）：
 * <ul>
 *   <li>分类列表显示全部热键（只读模式 = F1 速查表）</li>
 *   <li>点击热键进入修改模式（行内提示「按新热键...」）</li>
 *   <li>支持 Ctrl / Shift / Alt 组合键</li>
 *   <li>冲突检测：冲突时该行红色显示并拒绝写入</li>
 *   <li>重置单个 / 全部默认</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class HotkeySettingsPanel extends UIOverlayPanel
{
    /**
     * 是否为只读速查表模式（F1）
     */
    private final boolean cheatSheetMode;

    /**
     * 热键列表
     */
    private final HotkeyList list;

    /**
     * 正在等待用户输入新热键的动作 ID
     */
    private String waitingFor;

    public HotkeySettingsPanel(boolean cheatSheetMode)
    {
        super(IKey.constant(cheatSheetMode ? "热键速查表 (F1)" : "热键设置"));

        this.cheatSheetMode = cheatSheetMode;

        this.list = new HotkeyList((l) -> this.pickHotkey());
        this.list.relative(this.content).xy(0, 0).w(1F).h(1F, -26);

        UIElement controls = new UIElement();

        controls.relative(this.content).y(1F, -26).w(1F).h(26).row(5).resize();

        if (!cheatSheetMode)
        {
            UIButton resetOne = new UIButton(L10n.lang("bbs_ai.str.hotkeyPanel.1"), (b) -> this.resetSelected());

            resetOne.setEnabled(false);

            UIButton resetAll = new UIButton(L10n.lang("bbs_ai.str.hotkeyPanel.2"), (b) ->
            {
                HotkeyRegistry.get().resetAll();
                KeyMapConfig.save();
                this.list.refresh();
            });

            UIIcon save = new UIIcon(Icons.SAVE, (b) ->
            {
                KeyMapConfig.save();
            });

            save.tooltip(L10n.lang("bbs_ai.str.hotkeyPanel.3"));

            controls.add(resetOne, resetAll, save);
        }

        this.content.add(this.list, controls);
        this.list.refresh();
    }

    /**
     * 选中列表项：进入修改模式
     */
    private void pickHotkey()
    {
        if (this.cheatSheetMode)
        {
            return;
        }

        HotkeyMenuItem item = this.list.getCurrentItem();

        if (item != null)
        {
            this.waitingFor = item.hotkey.id;
        }
    }

    /**
     * 重置选中的热键
     */
    private void resetSelected()
    {
        HotkeyMenuItem item = this.list.getCurrentItem();

        if (item != null)
        {
            HotkeyRegistry.get().reset(item.hotkey.id);
            KeyMapConfig.save();
            this.list.refresh();
        }
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        /* 修改模式：按任意键写入新热键 */
        if (this.waitingFor != null && context.getKeyAction() == KeyAction.PRESSED)
        {
            int key = context.getKeyCode();
            int mods = 0;

            if (context.isHeld(GLFW.GLFW_KEY_LEFT_SHIFT) || context.isHeld(GLFW.GLFW_KEY_RIGHT_SHIFT)) mods |= HotkeyDefinition.MOD_SHIFT;
            if (context.isHeld(GLFW.GLFW_KEY_LEFT_CONTROL) || context.isHeld(GLFW.GLFW_KEY_RIGHT_CONTROL)) mods |= HotkeyDefinition.MOD_CTRL;
            if (context.isHeld(GLFW.GLFW_KEY_LEFT_ALT) || context.isHeld(GLFW.GLFW_KEY_RIGHT_ALT)) mods |= HotkeyDefinition.MOD_ALT;

            /* Esc 取消修改模式 */
            if (key == GLFW.GLFW_KEY_ESCAPE && mods == 0)
            {
                this.waitingFor = null;

                return true;
            }

            String conflict = HotkeyRegistry.get().rebind(this.waitingFor, key, mods);

            this.waitingFor = null;
            this.list.setConflict(conflict);
            KeyMapConfig.save();
            this.list.refresh();

            return true;
        }

        return super.subKeyPressed(context);
    }

    /**
     * 热键列表（左名称 + 右热键文本，双列自绘）
     */
    private static class HotkeyList extends UIStringList
    {
        /**
         * 行数据
         */
        private final List<HotkeyMenuItem> items = new ArrayList<>();

        /**
         * 冲突动作 ID（红色显示）
         */
        private String conflict;

        public HotkeyList(Consumer<List<String>> callback)
        {
            super(callback);
        }

        /**
         * 重建行数据
         */
        public void refresh()
        {
            this.items.clear();
            this.clear();

            for (HotkeyDefinition definition : HotkeyRegistry.get().getAll())
            {
                HotkeyMenuItem item = new HotkeyMenuItem(definition);

                this.items.add(item);
                this.add(this.rowText(item));
            }
        }

        /**
         * 设置冲突提示
         */
        public void setConflict(String id)
        {
            this.conflict = id;
        }

        /**
         * 获取当前选中项
         */
        public HotkeyMenuItem getCurrentItem()
        {
            int index = this.getIndex();

            return index >= 0 && index < this.items.size() ? this.items.get(index) : null;
        }

        /**
         * 行文本：动作名 + 分类 + 热键
         */
        private String rowText(HotkeyMenuItem item)
        {
            return item.getCategoryTitle() + " · " + item.getLabel() + " —— " + item.getHotkeyText() + (item.isModified() ? " *" : "");
        }

        @Override
        protected boolean sortElements()
        {
            return false;
        }

        @Override
        public void renderListElement(UIContext context, String element, int i, int x, int y, boolean hover, boolean selected)
        {
            super.renderListElement(context, element, i, x, y, hover, selected);

            if (i < 0 || i >= this.items.size())
            {
                return;
            }

            HotkeyMenuItem item = this.items.get(i);
            Batcher2D batcher = context.batcher;
            int color = Colors.GRAY;

            /* 冲突行红色警告 */
            if (this.conflict != null && this.conflict.equals(item.hotkey.id))
            {
                color = Colors.RED;
            }
            else if (item.isModified())
            {
                color = Colors.YELLOW;
            }

            batcher.text(item.getHotkeyText(), x + this.area.w - batcher.getFont().getWidth(item.getHotkeyText()) - 8, y + 5, color);
        }
    }
}
