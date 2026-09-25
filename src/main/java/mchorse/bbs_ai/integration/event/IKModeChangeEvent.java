package mchorse.bbs_ai.integration.event;

import mchorse.bbs_ai.ik.IKMode;

/**
 * IK 模式切换事件
 *
 * <p>原生 IK ↔ Blender 风格 IK 切换时发布。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class IKModeChangeEvent
{
    /**
     * 新模式
     */
    public final IKMode newMode;

    /**
     * 旧模式
     */
    public final IKMode oldMode;

    public IKModeChangeEvent(IKMode newMode, IKMode oldMode)
    {
        this.newMode = newMode;
        this.oldMode = oldMode;
    }
}
