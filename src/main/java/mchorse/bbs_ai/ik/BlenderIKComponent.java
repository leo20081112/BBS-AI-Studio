package mchorse.bbs_ai.ik;

import mchorse.bbs_ai.preview.PreviewSystem;
import mchorse.bbs_ai.import_manager.SourceType;
import mchorse.bbs_ai.format.MotionFrame;
import mchorse.bbs_ai.format.BonePose;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Blender IK 组件（Form 集成）
 *
 * <p>把 Blender IK 约束系统挂接到 BBS 的 Form 骨骼体系【原版兼容】：
 * <ul>
 *   <li>从 Form 当前姿态（{@link Pose}）构建骨骼链世界坐标</li>
 *   <li>调用 {@link BlenderIKSolver} 解算</li>
 *   <li>解算结果经 {@code IKSolveSink} 回调 / 直接写入预烘焙预览系统</li>
 * </ul></p>
 *
 * <p>接口契约（项目规范）：IK 解算结果 → 预览系统
 * {@code onIKSolved(目标, 骨骼旋转, tick)}。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BlenderIKComponent
{
    /**
     * 链名称
     */
    public final String name;

    /**
     * 约束参数
     */
    public final BlenderIKConstraint constraint = new BlenderIKConstraint();

    /**
     * 骨骼链
     */
    public final IKBoneChain chain;

    /**
     * 解算器
     */
    public final BlenderIKSolver solver = new BlenderIKSolver();

    /**
     * 是否启用该组件
     */
    public boolean enabled = true;

    /**
     * 解算发生的 tick（用于写入预览时间轴）
     */
    public int atTick;

    public BlenderIKComponent(String name, IKBoneChain chain)
    {
        this.name = name;
        this.chain = chain;
    }

    /**
     * 从 Form 姿态构建骨骼链（每根骨骼用相邻骨骼名推算世界位置由调用方提供）
     *
     * @param boneNames  骨骼名（末端 → 根部顺序）
     * @param headTails  骨骼名 → [头位置, 尾位置]（世界坐标）
     * @param pose       Form 当前姿态【原版兼容】
     */
    public static IKBoneChain buildChain(String chainName, List<String> boneNames, java.util.Map<String, Vector3f[]> headTails, Pose pose)
    {
        IKBoneChain chain = new IKBoneChain(chainName);

        for (String boneName : boneNames)
        {
            IKBone bone = new IKBone(boneName);
            Vector3f[] positions = headTails.get(boneName);

            if (positions != null && positions.length >= 2)
            {
                bone.setPositions(positions[0], positions[1]);
            }

            /* 记录 FK 原始旋转【原版兼容】（BBS 姿态为弧度，IK 内部统一用度） */
            if (pose != null)
            {
                PoseTransform transform = pose.get(boneName);

                if (transform != null)
                {
                    bone.restRotation.set(
                        (float) Math.toDegrees(transform.rotate.x),
                        (float) Math.toDegrees(transform.rotate.y),
                        (float) Math.toDegrees(transform.rotate.z)
                    );
                }
            }

            bone.rotation.set(bone.restRotation);
            chain.addBone(bone);
        }

        return chain;
    }

    /**
     * 执行一次解算并把结果推入预烘焙预览系统
     *
     * @param filmId  目标影片（可为空 = 未保存影片）
     * @param replayId 目标角色
     * @return 是否解算成功
     */
    public boolean solveAndStage(String filmId, String replayId)
    {
        if (!this.enabled)
        {
            return false;
        }

        boolean converged = this.solver.solve(this.chain, this.constraint);

        if (!converged)
        {
            /* 未收敛也允许进入预览（部分解算结果仍有价值） */
            System.out.println("[BBS AI] IK 链 " + this.name + " 未完全收敛");
        }

        /* 解算结果 → 预览系统 */
        MotionFrame frame = new MotionFrame(this.atTick);

        for (IKBone bone : this.chain.getBones())
        {
            frame.bone(bone.name, bone.rotation.x, bone.rotation.y, bone.rotation.z);
        }

        List<MotionFrame> frames = new ArrayList<>();

        frames.add(frame);

        PreviewSystem.get().stage(filmId, replayId, "", SourceType.INTERNAL_AI, frames);

        return converged;
    }

    /**
     * 读取解算后的骨骼旋转快照（供 Gizmo / 面板显示）
     */
    public List<float[]> snapshotRotations()
    {
        List<float[]> snapshot = new ArrayList<>();

        for (IKBone bone : this.chain.getBones())
        {
            snapshot.add(new float[] {bone.rotation.x, bone.rotation.y, bone.rotation.z});
        }

        return snapshot;
    }

    /**
     * 解算结果直接应用到 Form 姿态（跳过预览，仅调试用）【原版兼容】
     */
    public void applyToPose(Pose pose)
    {
        if (pose == null)
        {
            return;
        }

        for (IKBone bone : this.chain.getBones())
        {
            PoseTransform transform = pose.get(bone.name);

            if (transform != null)
            {
                transform.rotate.set(
                    (float) Math.toRadians(bone.rotation.x),
                    (float) Math.toRadians(bone.rotation.y),
                    (float) Math.toRadians(bone.rotation.z)
                );
            }
        }
    }
}
