package mchorse.bbs_ai.ui.transform;

import net.minecraft.client.MinecraftClient;
import mchorse.bbs_mod.ui.framework.UIScreen;

/**
 * Minecraft 键位覆盖管理器
 *
 * <p>解决 BBS GUI 打开时的输入冲突（项目规范模块 10「输入冲突解决」）：
 * <ul>
 *   <li>BBS 的 UIScreen 打开时会吞掉 Minecraft 的输入事件（原版行为），
 *       本管理器在此之上追踪 GUI 状态，决定 Blender / Mine-imator
 *       变换系统是否响应按键【原版兼容】</li>
 *   <li>变换模式进行中提示抑制 WASD 移动（BBS GUI 打开时 MC 输入本就暂停）</li>
 *   <li>关闭 GUI 时全部键位恢复原状——因为从不真正解绑 MC 键位，
 *       而是通过「只有 BBS UI 聚焦时才消费按键」的策略避免冲突</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class KeyBindingOverrideManager
{
    /**
     * 单例
     */
    private static final KeyBindingOverrideManager INSTANCE = new KeyBindingOverrideManager();

    /**
     * BBS UI 是否打开
     */
    private boolean bbsUiOpen;

    private KeyBindingOverrideManager()
    {}

    /**
     * 获取单例
     */
    public static KeyBindingOverrideManager get()
    {
        return INSTANCE;
    }

    /**
     * 刷新 BBS UI 状态（每帧调用）
     */
    public void update()
    {
        this.bbsUiOpen = MinecraftClient.getInstance().currentScreen instanceof UIScreen;
    }

    /**
     * BBS UI 是否打开（打开时我们的热键系统才接管按键）
     */
    public boolean isBbsUiOpen()
    {
        return this.bbsUiOpen;
    }

    /**
     * 变换系统是否可以响应按键（BBS UI 打开且没有文本框聚焦时）
     */
    public boolean canHandleKeys(mchorse.bbs_mod.ui.framework.UIContext context)
    {
        return this.bbsUiOpen && context != null && !context.isFocused();
    }
}
