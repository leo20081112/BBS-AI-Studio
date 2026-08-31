package mchorse.bbs_ai.preview;

/**
 * 预览 → 正式烘焙目标接口
 *
 * <p>实现类负责把预览轨道写入真实的编辑目标：
 * <ul>
 *   <li>客户端实现：写入仪表盘当前编辑中的 Film 对象（Live 编辑流）【原版兼容】</li>
 *   <li>服务端 / 无头实现：通过 FilmManager 落盘（离线流）【原版兼容】</li>
 * </ul></p>
 *
 * <p>接口契约（与项目规范一致）：
 * {@code applyKeyframes(角色, 关键帧, 模式)} —— 返回成功写入的关键帧数。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public interface BakeTarget
{
    /**
     * 把预览轨道正式写入目标
     *
     * @param context 预览上下文（影片 / 角色 / 表单 / 时间范围）
     * @param track   预览轨道
     * @param mode    烘焙模式
     * @return 写入的关键帧数量
     */
    int applyKeyframes(PreviewContext context, PreviewTrack track, BakeMode mode);
}
