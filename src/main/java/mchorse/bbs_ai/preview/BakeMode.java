package mchorse.bbs_ai.preview;

/**
 * 烘焙模式
 *
 * <p>确认烘焙时预览数据写入正式 Film 的策略：
 * <ul>
 *   <li>{@link #OVERWRITE} —— 覆盖时间范围内现有关键帧</li>
 *   <li>{@link #INSERT_BLEND} —— 保留现有帧，插入新帧并对边界 ±5 tick 线性混合</li>
 *   <li>{@link #CANCEL} —— 取消，清除预览缓存</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public enum BakeMode
{
    OVERWRITE,
    INSERT_BLEND,
    CANCEL
}
