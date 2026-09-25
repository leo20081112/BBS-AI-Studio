/**
 * bbs_ai_studio_motion_v1 统一数据契约
 * 
 * <p>游戏内生成、IK 调整与外部 Python 工具链共同遵循的动作数据格式
 * （{@link mchorse.bbs_ai.format.MotionData} 根对象 + 元信息/关键帧/骨骼姿态）。
 * 序列化为严格 JSON，字段名与 Python 端 core/motion_data.py 保持一致，改动需两侧同步。</p>
 */
package mchorse.bbs_ai.format;
