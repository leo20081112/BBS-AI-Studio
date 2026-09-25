package mchorse.bbs_ai.ik;

import org.joml.Quaternionf;

import java.util.Map;

/**
 * IK 解算结果 → 预览系统接口契约
 *
 * <p>项目规范定义的核心数据流接口：IK 解算完成后由解算方调用。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public interface IKSolveSink
{
    /**
     * IK 解算完成回调
     *
     * @param target        目标角色 ID
     * @param boneRotations 骨骼名 → 旋转（四元数）
     * @param atTick        解算时刻（tick）
     */
    void onIKSolved(String target, Map<String, Quaternionf> boneRotations, float atTick);
}
