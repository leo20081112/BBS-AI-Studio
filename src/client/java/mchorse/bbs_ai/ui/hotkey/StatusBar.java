package mchorse.bbs_ai.ui.hotkey;

import java.util.ArrayList;
import java.util.List;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * 底部状态栏
 *
 * <p>实时显示当前上下文的可用热键提示（项目规范模块 11）：
 * <ul>
 *   <li>变换中：「移动中 | 左键确认 | 右键取消 | X/Y/Z 约束轴 | Shift 精确」</li>
 *   <li>预览模式：「预览模式 | Ctrl+Enter 烘焙 | Ctrl+Esc 放弃 | 方向键逐帧」</li>
 *   <li>默认：「G 移动 | R 旋转 | S 缩放 | I 关键帧 | 空格 播放 | N 属性面板」</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class StatusBar extends UIElement
{
    /**
     * 状态类型
     */
    public enum State
    {
        DEFAULT,
        TRANSFORM,
        PREVIEW
    }

    /**
     * 当前状态
     */
    private State state = State.DEFAULT;

    /**
     * 附加消息（临时显示）
     */
    private String extraMessage = "";

    public StatusBar()
    {
        this.h(16);
    }

    /**
     * 切换状态提示
     */
    public void setState(State state)
    {
        this.state = state == null ? State.DEFAULT : state;
    }

    /**
     * 设置附加消息（随下一帧渲染显示）
     */
    public void setExtraMessage(String message)
    {
        this.extraMessage = message == null ? "" : message;
    }

    /**
     * 构建当前状态的提示文本
     */
    private String buildHint()
    {
        HotkeyRegistry registry = HotkeyRegistry.get();
        List<String> parts = new ArrayList<>();

        switch (this.state)
        {
            case TRANSFORM ->
            {
                parts.add("移动中");
                parts.add("左键确认");
                parts.add("右键取消");
                parts.add("X/Y/Z 约束轴");
                parts.add("Shift 精确");
            }
            case PREVIEW ->
            {
                parts.add("预览模式");
                parts.add(hint(registry, "ai.preview_bake"));
                parts.add(hint(registry, "ai.preview_discard"));
                parts.add("方向键逐帧");
            }
            default ->
            {
                parts.add(hint(registry, "transform.grab"));
                parts.add(hint(registry, "transform.rotate"));
                parts.add(hint(registry, "transform.scale"));
                parts.add(hint(registry, "keyframe.insert"));
                parts.add(hint(registry, "timeline.play"));
                parts.add(hint(registry, "panel.n_panel"));
            }
        }

        if (!this.extraMessage.isEmpty())
        {
            parts.add(this.extraMessage);
        }

        return String.join("  |  ", parts);
    }

    /**
     * 「动作名 热键」格式的提示
     */
    private String hint(HotkeyRegistry registry, String id)
    {
        HotkeyDefinition definition = registry.get(id);

        if (definition == null)
        {
            return id;
        }

        return KeyFormatter.format(definition) + " " + definition.name;
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        Batcher2D batcher = context.batcher;

        this.area.render(batcher, BBSSettings.chromeSurface());

        String hint = this.buildHint();

        batcher.text(hint, this.area.x + 6, this.area.my(batcher.getFont().getHeight()), Colors.GRAY);
    }
}
