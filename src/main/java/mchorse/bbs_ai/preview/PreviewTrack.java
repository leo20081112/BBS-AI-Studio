package mchorse.bbs_ai.preview;

import mchorse.bbs_ai.format.MotionFrame;
import mchorse.bbs_ai.motion.SkeletonMapper;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 预览轨道数据
 *
 * <p>存储一次预览的全部临时关键帧。除 {@link MotionFrame} 列表（烘焙数据源）外，
 * 还以 {@link FloatBuffer} 连续存储旋转数据（帧 × 骨骼 × 3 轴，按骨骼序
 * {@link SkeletonMapper#ALL_BONES} 排列），供渲染层高频采样，避免遍历对象图。</p>
 *
 * <p>线程契约：数据在后台线程生成后不再修改；渲染线程只读。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class PreviewTrack
{
    /**
     * 轨道类型标识（motion / ik / storyboard）
     */
    public final String trackType;

    /**
     * 临时关键帧（按 tick 升序，只读使用）
     */
    private final List<MotionFrame> frames;

    /**
     * 旋转数据缓冲：frameCount × boneCount × 3
     */
    private final FloatBuffer rotationBuffer;

    /**
     * 帧刻度数组（与 rotationBuffer 帧序对齐，供快速查找）
     */
    private final float[] ticks;

    public PreviewTrack(String trackType, List<MotionFrame> frames)
    {
        this.trackType = trackType;
        this.frames = new ArrayList<>(frames);

        /* 填充旋转缓冲（缺失帧 / 缺失骨骼填 0） */
        int boneCount = SkeletonMapper.ALL_BONES.length;

        this.rotationBuffer = FloatBuffer.allocate(Math.max(1, this.frames.size() * boneCount * 3));
        this.ticks = new float[this.frames.size()];

        for (int f = 0; f < this.frames.size(); f++)
        {
            MotionFrame frame = this.frames.get(f);

            this.ticks[f] = frame.tick;

            for (int b = 0; b < boneCount; b++)
            {
                String bone = SkeletonMapper.ALL_BONES[b];
                float[] rotation = frame.bones.containsKey(bone) ? frame.bones.get(bone).rotation : null;

                if (rotation != null)
                {
                    this.rotationBuffer.put(rotation[0]);
                    this.rotationBuffer.put(rotation[1]);
                    this.rotationBuffer.put(rotation[2]);
                }
                else
                {
                    this.rotationBuffer.put(0.0F).put(0.0F).put(0.0F);
                }
            }
        }

        this.rotationBuffer.flip();
    }

    /**
     * 关键帧列表（快照，只读）
     */
    public List<MotionFrame> getFrames()
    {
        return Collections.unmodifiableList(this.frames);
    }

    /**
     * 帧数
     */
    public int getFrameCount()
    {
        return this.frames.size();
    }

    /**
     * 起始 tick
     */
    public int getStartTick()
    {
        return this.frames.isEmpty() ? 0 : this.frames.get(0).tick;
    }

    /**
     * 结束 tick
     */
    public int getEndTick()
    {
        return this.frames.isEmpty() ? 0 : this.frames.get(this.frames.size() - 1).tick;
    }

    /**
     * 旋转缓冲（只读视图），布局：帧 × 骨骼 × 3 轴（度）
     */
    public FloatBuffer getRotationBuffer()
    {
        return this.rotationBuffer.duplicate();
    }

    /**
     * 读取指定帧序号、指定骨骼的三轴旋转（度）
     */
    public void getRotation(int frameIndex, int boneIndex, float[] out)
    {
        int base = (frameIndex * SkeletonMapper.ALL_BONES.length + boneIndex) * 3;

        out[0] = this.rotationBuffer.get(base);
        out[1] = this.rotationBuffer.get(base + 1);
        out[2] = this.rotationBuffer.get(base + 2);
    }

    /**
     * 帧刻度数组（只读）
     */
    public float[] getTicks()
    {
        return this.ticks;
    }

    /**
     * 统计涉及的骨骼列表
     */
    public List<String> getAffectedBones()
    {
        List<String> bones = new ArrayList<>();

        for (String bone : SkeletonMapper.ALL_BONES)
        {
            for (MotionFrame frame : this.frames)
            {
                if (frame.bones.containsKey(bone))
                {
                    bones.add(bone);

                    break;
                }
            }
        }

        return bones;
    }
}
