package mchorse.bbs_ai.ik;

import org.joml.Vector3f;

/**
 * AI 锚定共享组件（client 单例）
 *
 * <p>约束面板 / 角色属性页锚定控件 / AI 锚定视口工具共用同一条 IK 链与约束，
 * 保证三处（2.7 新体系 M1/M3②/M3①）状态一致。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class AIAnchorConfig
{
    /**
     * 共享 IK 组件（head 演示链；绑定实际模型骨骼后即为真实链）
     */
    public static final BlenderIKComponent COMPONENT;

    static
    {
        IKBoneChain chain = new IKBoneChain("ik_ai");

        IKBone head = new IKBone("head");

        head.setPositions(new Vector3f(0.0F, 1.4F, 0.0F), new Vector3f(0.0F, 1.9F, 0.0F));
        chain.addBone(head);

        IKBone body = new IKBone("body");

        body.setPositions(new Vector3f(0.0F, 0.7F, 0.0F), new Vector3f(0.0F, 1.4F, 0.0F));
        chain.addBone(body);

        COMPONENT = new BlenderIKComponent("ik_ai", chain);
    }

    private AIAnchorConfig()
    {}
}
