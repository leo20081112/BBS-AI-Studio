package mchorse.bbs_ai.ik;

import org.joml.Vector3f;

import java.util.List;

/**
 * Blender IK 解算器（完整实现）
 *
 * <p>解算流程（与 Blender 语义一致）：
 * <ol>
 *   <li>锚点跟随（Follow / useAnchor）：目标超出可达距离时，根骨骼向目标平移补差</li>
 *   <li>链长裁剪（Chain Length）：只影响从末端向上的 N 根骨骼</li>
 *   <li>CCD 迭代解算（从末端向根部旋转骨骼指向目标）</li>
 *   <li>Pole Target 控制弯曲平面（肘 / 膝方向）</li>
 *   <li>应用每骨骼旋转限制（Rotation Limits）</li>
 *   <li>应用拉伸（Stretch + Stretch Limit）</li>
 *   <li>FK/IK 混合（Influence + Weight Falloff）</li>
 *   <li>目标旋转（Target Rotation）复制到末端骨骼</li>
 * </ol></p>
 *
 * <p>验证点（对应项目规范）：
 * <ul>
 *   <li>useAnchor=true：目标远离时根骨骼跟随移动</li>
 *   <li>useAnchor=false：根骨骼固定，末端尽量指向目标</li>
 *   <li>Pole Target 正确控制膝盖 / 手肘方向</li>
 *   <li>Chain Length 正确限制影响范围</li>
 *   <li>Rotation Limits 正确限制每根骨骼</li>
 *   <li>Influence 正确混合 FK/IK</li>
 *   <li>Stretch 正确拉伸骨骼链</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class BlenderIKSolver
{
    /**
     * CCD 最大迭代次数
     */
    private static final int MAX_ITERATIONS = 32;

    /**
     * 收敛阈值（世界单位）
     */
    private static final float CONVERGENCE = 0.001F;

    /**
     * 解算一条 IK 链
     *
     * @param chain      骨骼链（末端优先）
     * @param constraint 约束参数
     * @return 是否收敛
     */
    public boolean solve(IKBoneChain chain, BlenderIKConstraint constraint)
    {
        if (chain == null || constraint == null || chain.size() == 0)
        {
            return false;
        }

        List<IKBone> bones = chain.getBones();
        IKBone rootBone = chain.getRootBone();

        /* 1. 锚点跟随：决定根骨骼是否移动 */
        if (constraint.useAnchor && rootBone != null)
        {
            float reach = constraint.calculateReach(chain);
            float stretchLimit = Math.max(1.0F, constraint.stretchLimit);
            float restLength = chain.calculateRestLength();
            /* 可达半径 = 静态总长 × 拉伸上限（未启用拉伸时上限为 1） */
            float effectiveReach = restLength * (constraint.useStretch ? stretchLimit : 1.0F);

            float distanceToTarget = rootBone.head.distance(constraint.getTargetLocal());

            if (distanceToTarget > effectiveReach && distanceToTarget > 0.0001F)
            {
                /* 根骨骼沿目标方向平移超出量 */
                Vector3f direction = new Vector3f(constraint.getTargetLocal()).sub(rootBone.head).normalize();
                Vector3f offset = direction.mul(distanceToTarget - effectiveReach);

                rootBone.head.add(offset);
                rootBone.tail.add(offset);

                /* 链上其余骨骼同步平移，保持链结构 */
                for (IKBone bone : bones)
                {
                    if (bone != rootBone)
                    {
                        bone.head.add(offset);
                        bone.tail.add(offset);
                    }
                }
            }
        }

        /* 2. 记录 FK 原始旋转（Influence 混合需要） */
        for (IKBone bone : bones)
        {
            bone.resetToRest();
        }

        /* 3. 链长裁剪：受影响的骨骼为末端向上 N 根 */
        int affected = constraint.chainLength <= 0 ? bones.size() : Math.min(constraint.chainLength, bones.size());

        /* 4. CCD 迭代 */
        boolean converged = false;

        for (int iteration = 0; iteration < MAX_ITERATIONS && !converged; iteration++)
        {
            converged = true;

            /* 从末端向根部遍历受影响骨骼 */
            for (int index = 0; index < affected; index++)
            {
                IKBone bone = bones.get(index);

                IKBone endBone = constraint.useTail ? chain.getTipBone() : this.endHeadBone(chain, affected);
                Vector3f end = constraint.useTail ? endBone.tail : endBone.head;
                float distanceBefore = end.distance(constraint.getTargetLocal());

                if (distanceBefore <= CONVERGENCE)
                {
                    break;
                }

                /* 计算当前骨骼使末端转向目标所需旋转增量 */
                Vector3f toEnd = new Vector3f(end).sub(bone.head);
                Vector3f toTarget = new Vector3f(constraint.getTargetLocal()).sub(bone.head);

                if (toEnd.lengthSquared() < 1.0E-8F || toTarget.lengthSquared() < 1.0E-8F)
                {
                    continue;
                }

                toEnd.normalize();
                toTarget.normalize();

                float angle = (float) Math.acos(Math.max(-1.0F, Math.min(1.0F, toEnd.dot(toTarget))));

                if (angle < 0.001F)
                {
                    continue;
                }

                converged = false;

                /* 旋转轴 = toEnd × toTarget，随后应用极向修正 */
                Vector3f axis = new Vector3f(toEnd).cross(toTarget);

                if (axis.lengthSquared() < 1.0E-8F)
                {
                    continue;
                }

                axis.normalize();

                /* Pole Target：把旋转轴投影约束到极向平面 */
                axis = this.applyPoleTarget(axis, bone, constraint);

                /* 权重衰减 */
                float weight = constraint.weightFor(index, affected);
                float weightedAngle = angle * weight;

                /* 把轴角旋转增量转换为骨骼局部欧拉增量（度） */
                Vector3f delta = axisAngleToEulerDegrees(axis, weightedAngle);

                bone.rotation.add(delta);
                bone.applyLimits();
                bone.rotation.x = clampBoneLimit(bone, constraint, 0);
                bone.rotation.z = clampBoneLimit(bone, constraint, 2);

                /* 旋转骨骼：重新计算该骨骼之后的整条子链位置 */
                this.rotateSubchain(chain, index, axis, weightedAngle);
            }
        }

        /* 5. 拉伸：仍不可达时按比例拉伸各骨骼 */
        if (constraint.useStretch)
        {
            this.applyStretch(chain, constraint);
        }

        /* 6. FK/IK 混合（Influence） */
        if (constraint.influence < 0.9999F)
        {
            for (IKBone bone : bones)
            {
                bone.rotation.x = bone.restRotation.x + (bone.rotation.x - bone.restRotation.x) * constraint.influence;
                bone.rotation.y = bone.restRotation.y + (bone.rotation.y - bone.restRotation.y) * constraint.influence;
                bone.rotation.z = bone.restRotation.z + (bone.rotation.z - bone.restRotation.z) * constraint.influence;
            }
        }

        /* 7. 目标旋转：末端骨骼复制目标旋转 */
        if (constraint.useTargetRotation)
        {
            IKBone tip = chain.getTipBone();

            if (tip != null)
            {
                tip.rotation.set(constraint.targetRotation);
            }
        }

        /* 8. 锚点跟随：末端（手/脚）旋转时的锚点联动 */
        this.applyAnchorFollow(chain, constraint);

        return converged;
    }

    /**
     * 锚点跟随补偿
     *
     * <p>两种模式（约束面板开关）：
     * <ul>
     *   <li><b>脚部贴地（mode=1）</b>：锚定后末端端点被钉回锚定位置（贴地点），
     *       身体/腿移动或旋转造成末端偏移时整链向锚点回位 —— 脚底不滑</li>
     *   <li><b>手部抓附（mode=2）</b>：末端旋转相对锚定快照的偏移 ΔR 把锚定点
     *       绕末端骨骼头部旋转，锚点走到的位置即补偿后的目标方向 —— 抓附点随手转</li>
     * </ul>
     * 末端旋转偏移超过释放阈值后锚定释放（脚抬步 / 手松开），下一次解算在新位置重新锚定。</p>
     */
    private void applyAnchorFollow(IKBoneChain chain, BlenderIKConstraint constraint)
    {
        if (constraint.anchorMode == 0)
        {
            constraint.anchored = false;

            return;
        }

        IKBone tip = chain.getTipBone();

        if (tip == null)
        {
            return;
        }

        Vector3f tipEnd = constraint.useTail ? tip.tail : tip.head;

        /* 未锚定：记录锚点与末端姿态快照，进入锚定状态 */
        if (!constraint.anchored)
        {
            constraint.anchored = true;
            constraint.anchorPoint.set(tipEnd);
            constraint.anchorRestRotation.set(tip.rotation);

            return;
        }

        /* 已锚定：末端旋转相对快照的偏移量（度，欧拉空间近似） */
        Vector3f rotationDelta = new Vector3f(tip.rotation).sub(constraint.anchorRestRotation);
        float deltaAngle = rotationDelta.length();

        /* 超过释放阈值：释放锚定（下帧在新位置重新锚定） */
        if (deltaAngle > constraint.anchorReleaseAngle)
        {
            constraint.anchored = false;

            return;
        }

        Vector3f compensation = new Vector3f();

        if (constraint.anchorMode == 1)
        {
            /* 脚部贴地：末端端点钉回锚定位置（水平防滑 + 垂直贴地） */
            compensation.set(constraint.anchorPoint).sub(tipEnd);
        }
        else
        {
            /* 手部抓附：锚定点随 ΔR 绕末端骨骼头部旋转，链跟随旋转后的锚点 */
            org.joml.Matrix4f anchorRotation = new org.joml.Matrix4f().rotationXYZ(
                (float) Math.toRadians(rotationDelta.x),
                (float) Math.toRadians(rotationDelta.y),
                (float) Math.toRadians(rotationDelta.z)
            );

            Vector3f anchored = new Vector3f(constraint.anchorPoint).sub(tip.head);

            anchorRotation.transformDirection(anchored);
            anchored.add(tip.head);

            compensation.set(anchored).sub(tipEnd);
        }

        compensation.mul(constraint.anchorStrength);

        /* 补偿量作用于整条链（锚点不动，身体/根被拉回） */
        if (compensation.lengthSquared() > 1.0E-10F)
        {
            for (IKBone bone : chain.getBones())
            {
                bone.head.add(compensation);
                bone.tail.add(compensation);
            }
        }
    }

    /**
     * 取受影响链段的末端骨骼（Use Tail = false 时使用受影响末端骨骼的头点）
     */
    private IKBone endHeadBone(IKBoneChain chain, int affected)
    {
        return chain.getBones().get(Math.max(0, affected - 1));
    }

    /**
     * 极向目标修正：把旋转轴拉向「骨骼方向 × 极向方向」确定的弯曲平面法线
     */
    private Vector3f applyPoleTarget(Vector3f axis, IKBone bone, BlenderIKConstraint constraint)
    {
        if (constraint.poleTarget == null)
        {
            return axis;
        }

        /* 极向方向（世界空间，附加极向角度偏移） */
        Vector3f poleDirection = new Vector3f(constraint.poleTarget).sub(bone.head);

        if (poleDirection.lengthSquared() < 1.0E-8F)
        {
            return axis;
        }

        poleDirection.normalize();

        float poleAngleRad = (float) Math.toRadians(constraint.poleAngle);

        /* 围绕骨骼主轴（head→tail）旋转极向方向 poleAngle 度 */
        Vector3f boneDirection = new Vector3f(bone.tail).sub(bone.head);

        if (boneDirection.lengthSquared() < 1.0E-8F)
        {
            return axis;
        }

        boneDirection.normalize();
        rotateAroundAxis(poleDirection, boneDirection, poleAngleRad);

        /* 期望弯曲法线 = 骨骼方向 × 极向方向 */
        Vector3f desired = new Vector3f(boneDirection).cross(poleDirection);

        if (desired.lengthSquared() < 1.0E-8F)
        {
            return axis;
        }

        desired.normalize();

        /* CCD 轴向期望平面法线混合（保留收敛性） */
        return axis.lerp(desired, 0.5F).normalize();
    }

    /**
     * 旋转向量（Rodrigues 公式）
     */
    private void rotateAroundAxis(Vector3f vector, Vector3f axis, float angle)
    {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);

        Vector3f cross = new Vector3f(axis).cross(vector);
        float dot = axis.dot(vector);

        vector.set(
            vector.x * cos + cross.x * sin + axis.x * dot * (1.0F - cos),
            vector.y * cos + cross.y * sin + axis.y * dot * (1.0F - cos),
            vector.z * cos + cross.z * sin + axis.z * dot * (1.0F - cos)
        );
    }

    /**
     * 把受影响骨骼 index（含）之后的子链绕骨骼头点旋转，
     * 重建世界位置（保持骨骼长度）
     */
    private void rotateSubchain(IKBoneChain chain, int boneIndex, Vector3f axis, float angle)
    {
        List<IKBone> bones = chain.getBones();
        IKBone pivot = bones.get(boneIndex);
        Vector3f pivotPoint = new Vector3f(pivot.head);

        /* 构建旋转矩阵 */
        org.joml.Matrix3f rotation = new org.joml.Matrix3f().rotation(new org.joml.AxisAngle4f(angle, axis.x, axis.y, axis.z));

        for (int i = 0; i <= boneIndex; i++)
        {
            IKBone bone = bones.get(i);

            bone.head.sub(pivotPoint).mul(rotation).add(pivotPoint);
            bone.tail.sub(pivotPoint).mul(rotation).add(pivotPoint);
        }
    }

    /**
     * 应用拉伸：目标超出 reach 时把链整体拉伸到目标方向
     */
    private void applyStretch(IKBoneChain chain, BlenderIKConstraint constraint)
    {
        IKBone root = chain.getRootBone();

        if (root == null)
        {
            return;
        }

        float restLength = chain.calculateRestLength();
        float distance = root.head.distance(constraint.getTargetLocal());

        if (distance <= restLength)
        {
            return;
        }

        float stretchLimit = Math.max(1.0F, constraint.stretchLimit);
        float factor = Math.min(distance / restLength, stretchLimit);

        /* 按比例拉伸每根骨骼（沿其自身方向） */
        for (IKBone bone : chain.getBones())
        {
            Vector3f direction = new Vector3f(bone.tail).sub(bone.head);

            if (direction.lengthSquared() < 1.0E-8F)
            {
                continue;
            }

            float length = direction.length();

            direction.normalize().mul(length * factor);
            bone.tail.set(new Vector3f(bone.head).add(direction));
        }
    }

    /**
     * 轴角 → 欧拉增量（度）
     */
    private static Vector3f axisAngleToEulerDegrees(Vector3f axis, float angle)
    {
        /* 小角度近似：各轴分量为轴向量 × 角度 */
        return new Vector3f(
            (float) Math.toDegrees(axis.x * angle),
            (float) Math.toDegrees(axis.y * angle),
            (float) Math.toDegrees(axis.z * angle)
        );
    }

    /**
     * 限制某骨骼指定轴的旋转（读取约束中的 Rotation Limits）
     *
     * @param axis 0 = X，1 = Y，2 = Z
     */
    private static float clampBoneLimit(IKBone bone, BlenderIKConstraint constraint, int axis)
    {
        float value = axis == 0 ? bone.rotation.x : axis == 1 ? bone.rotation.y : bone.rotation.z;
        BlenderIKConstraint.BoneLimit limit = constraint.getLimit(bone.name);

        if (limit == null || !limit.enabled)
        {
            return value;
        }

        float min = axis == 0 ? limit.min.x : axis == 1 ? limit.min.y : limit.min.z;
        float max = axis == 0 ? limit.max.x : axis == 1 ? limit.max.y : limit.max.z;

        return Math.max(min, Math.min(max, value));
    }
}
