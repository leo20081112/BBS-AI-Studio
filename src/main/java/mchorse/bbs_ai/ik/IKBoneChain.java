package mchorse.bbs_ai.ik;

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * IK 骨骼链
 *
 * <p>按「末端 → 根部」顺序持有骨骼（index 0 = 末端骨骼，如手腕；
 * 最后一根 = 链受 {@code chainLength} 影响的最高骨骼）。
 * 提供 FK 正向传播与全链长度计算。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class IKBoneChain
{
    /**
     * 链名称（如 left_arm_ik）
     */
    public final String name;

    /**
     * 骨骼列表：index 0 = 末端（tip），size-1 = 根部（root）
     */
    private final List<IKBone> bones = new ArrayList<>();

    public IKBoneChain(String name)
    {
        this.name = name;
    }

    /**
     * 追加骨骼（按从末端到根部的顺序调用）
     */
    public void addBone(IKBone bone)
    {
        this.bones.add(bone);
    }

    /**
     * 全部骨骼（末端优先，只读语义）
     */
    public List<IKBone> getBones()
    {
        return this.bones;
    }

    /**
     * 末端骨骼（如手 / 脚）
     */
    public IKBone getTipBone()
    {
        return this.bones.isEmpty() ? null : this.bones.get(0);
    }

    /**
     * 根部骨骼（如上臂 / 大腿）
     */
    public IKBone getRootBone()
    {
        return this.bones.isEmpty() ? null : this.bones.get(this.bones.size() - 1);
    }

    /**
     * 骨骼数量
     */
    public int size()
    {
        return this.bones.size();
    }

    /**
     * 计算全部骨骼静态长度之和（reach 的基础）
     */
    public float calculateRestLength()
    {
        float total = 0.0F;

        for (IKBone bone : this.bones)
        {
            total += bone.restLength;
        }

        return total;
    }

    /**
     * 按当前骨骼旋转做一次 FK 正向传播，重建各骨骼头尾世界位置
     *
     * @param rootPosition 根骨骼头的世界位置
     */
    public void forwardKinematics(Vector3f rootPosition)
    {
        Vector3f cursor = new Vector3f(rootPosition);
        Vector3f direction = new Vector3f(0.0F, -1.0F, 0.0F);

        /* 从根部向末端遍历 */
        for (int i = this.bones.size() - 1; i >= 0; i--)
        {
            IKBone bone = this.bones.get(i);

            bone.head.set(cursor);

            /* 应用本骨骼旋转增量到方向（X/Z 为主摆轴） */
            applyEulerToDirection(direction, bone.rotation);

            /* 归一化后按当前长度（可能被拉伸）推进 */
            float length = bone.tail.distance(bone.head);

            if (length <= 0.0001F)
            {
                length = bone.restLength;
            }

            cursor = new Vector3f(cursor).add(new Vector3f(direction).mul(length));
            bone.tail.set(cursor);
        }
    }

    /**
     * 把欧拉旋转（度）应用到方向向量（小角度近似链式旋转）
     */
    private void applyEulerToDirection(Vector3f direction, Vector3f rotation)
    {
        float radX = (float) Math.toRadians(rotation.x);
        float radY = (float) Math.toRadians(rotation.y);
        float radZ = (float) Math.toRadians(rotation.z);

        /* 绕 X（前后摆） */
        float cos = (float) Math.cos(radX);
        float sin = (float) Math.sin(radX);
        float y = direction.y * cos - direction.z * sin;
        float z = direction.y * sin + direction.z * cos;

        direction.y = y;
        direction.z = z;

        /* 绕 Z（左右摆） */
        cos = (float) Math.cos(radZ);
        sin = (float) Math.sin(radZ);
        float x = direction.x * cos - direction.y * sin;

        y = direction.x * sin + direction.y * cos;

        direction.x = x;
        direction.y = y;

        /* 绕 Y（扭转） */
        cos = (float) Math.cos(radY);
        sin = (float) Math.sin(radY);
        x = direction.x * cos + direction.z * sin;
        z = -direction.x * sin + direction.z * cos;

        direction.x = x;
        direction.z = z;
    }
}
