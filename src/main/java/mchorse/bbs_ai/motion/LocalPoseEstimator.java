package mchorse.bbs_ai.motion;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import mchorse.bbs_ai.BBSAIStudio;
import mchorse.bbs_ai.core.api.APIException;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 本地姿态估计器（ONNX Runtime 推理引擎）
 *
 * <p>支持两类 ONNX 模型（用户自行下载放置到
 * {@code config/bbs/ai_cache/models/} 目录）：
 * <ul>
 *   <li>YOLOv8-pose（17 关键点，COCO）：输出 [1, 56, N]，含 NMS 解码</li>
 *   <li>RTMPose（17 关键点，SimCC 表示）：输出 simcc_x [1,17,W/2] 与 simcc_y [1,17,H/2]</li>
 * </ul></p>
 *
 * <p>模型文件缺失时抛出带清晰指引的 {@link APIException}，绝不崩溃。</p>
 *
 * <p>全部方法为阻塞式，必须在后台线程调用。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class LocalPoseEstimator implements AutoCloseable
{
    /**
     * 姿态模型种类
     */
    public enum PoseModel
    {
        /**
         * YOLOv8-pose（输入 640x640，目标检测式）
         */
        YOLOV8("yolov8n-pose.onnx", 640, 640),

        /**
         * RTMPose（输入 192x256，单人 SimCC）
         */
        RTMPOSE("rtmpose-m.onnx", 192, 256);

        /**
         * 默认模型文件名
         */
        public final String fileName;

        /**
         * 模型输入宽度
         */
        public final int inputWidth;

        /**
         * 模型输入高度
         */
        public final int inputHeight;

        PoseModel(String fileName, int inputWidth, int inputHeight)
        {
            this.fileName = fileName;
            this.inputWidth = inputWidth;
            this.inputHeight = inputHeight;
        }
    }

    /**
     * ONNX 运行时环境（进程内单例）
     */
    private static OrtEnvironment environment;

    /**
     * 推理会话
     */
    private final OrtSession session;

    /**
     * 当前模型种类
     */
    private final PoseModel model;

    /**
     * 输入节点名
     */
    private final String inputName;

    /**
     * 获取或创建 ONNX 运行时环境
     */
    private static OrtEnvironment getEnvironment() throws APIException
    {
        if (environment == null)
        {
            try
            {
                environment = OrtEnvironment.getEnvironment();
            }
            catch (Throwable t)
            {
                throw new APIException(APIException.ErrorCode.LOCAL_INFERENCE_ERROR,
                    "ONNX Runtime 初始化失败：" + t.getMessage(), -1, t);
            }
        }

        return environment;
    }

    /**
     * 获取模型目录 {@code config/bbs/ai_cache/models/}
     */
    public static File getModelsFolder()
    {
        return new File(BBSAIStudio.getAICacheFolder(), "models");
    }

    /**
     * 解析模型文件（存在性校验 + 明确错误提示）
     */
    public static File resolveModelFile(PoseModel poseModel) throws APIException
    {
        File file = new File(getModelsFolder(), poseModel.fileName);

        if (!file.isFile())
        {
            throw new APIException(APIException.ErrorCode.LOCAL_INFERENCE_ERROR,
                "未找到姿态模型文件：" + file.getAbsolutePath()
                    + "。请从官方发布页下载 " + poseModel.fileName + " 并放到上述目录");
        }

        return file;
    }

    /**
     * 创建估计器（加载模型）
     *
     * @param poseModel 模型种类
     * @throws APIException 模型缺失或加载失败
     */
    public LocalPoseEstimator(PoseModel poseModel) throws APIException
    {
        this.model = poseModel;
        this.session = this.createSession(resolveModelFile(poseModel));
        this.inputName = this.session.getInputNames().iterator().next();
    }

    /**
     * 创建推理会话
     */
    private OrtSession createSession(File modelFile) throws APIException
    {
        try
        {
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();

            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);

            return getEnvironment().createSession(modelFile.getAbsolutePath(), options);
        }
        catch (Throwable t)
        {
            throw new APIException(APIException.ErrorCode.LOCAL_INFERENCE_ERROR,
                "模型加载失败：" + modelFile.getName() + "，" + t.getMessage(), -1, t);
        }
    }

    /**
     * 估计单帧图像的人体关键点（阻塞式）
     *
     * @param frame         帧图像
     * @param sourceFrame   源帧序号
     * @param minConfidence 最低置信度
     * @return 关键点结果（坐标归一化 0~1）
     * @throws APIException 推理失败
     */
    public PoseKeypoints estimate(BufferedImage frame, int sourceFrame, float minConfidence) throws APIException
    {
        try
        {
            float[] input = this.preprocess(frame);

            try (OnnxTensor tensor = OnnxTensor.createTensor(getEnvironment(), FloatBuffer.wrap(input),
                new long[] {1, 3, this.model.inputHeight, this.model.inputWidth}))
            {
                try (OrtSession.Result result = this.session.run(Collections.singletonMap(this.inputName, tensor)))
                {
                    return this.decode(result, frame.getWidth(), frame.getHeight(), sourceFrame, minConfidence);
                }
            }
        }
        catch (APIException e)
        {
            throw e;
        }
        catch (Throwable t)
        {
            throw new APIException(APIException.ErrorCode.LOCAL_INFERENCE_ERROR, "推理失败：" + t.getMessage(), -1, t);
        }
    }

    /**
     * 图像预处理：缩放到模型输入尺寸 + letterbox + CHW 归一化
     */
    private float[] preprocess(BufferedImage frame)
    {
        int targetW = this.model.inputWidth;
        int targetH = this.model.inputHeight;

        /* 缩放绘制到目标画布（简单拉伸；YOLO 检测对轻度拉伸有容忍度） */
        BufferedImage resized = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);

        java.awt.Graphics2D graphics = resized.createGraphics();

        graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(frame, 0, 0, targetW, targetH, null);
        graphics.dispose();

        /* HWC → CHW，归一化 0~1 */
        float[] data = new float[3 * targetW * targetH];
        int[] pixels = resized.getRGB(0, 0, targetW, targetH, null, 0, targetW);
        int plane = targetW * targetH;

        for (int i = 0; i < plane; i++)
        {
            int pixel = pixels[i];

            data[i] = ((pixel >> 16) & 0xFF) / 255.0F;
            data[plane + i] = ((pixel >> 8) & 0xFF) / 255.0F;
            data[2 * plane + i] = (pixel & 0xFF) / 255.0F;
        }

        return data;
    }

    /**
     * 根据模型种类解码输出张量
     */
    private PoseKeypoints decode(OrtSession.Result result, int frameWidth, int frameHeight, int sourceFrame, float minConfidence) throws APIException, OrtException
    {
        if (this.model == PoseModel.RTMPOSE)
        {
            return this.decodeSimCC(result, frameWidth, frameHeight, sourceFrame, minConfidence);
        }

        return this.decodeYolo(result, frameWidth, frameHeight, sourceFrame, minConfidence);
    }

    /**
     * 解码 YOLOv8-pose 输出 [1, 56, N]：
     * 行 0~3 为边界框 (cx, cy, w, h)，行 4 为目标置信度，
     * 行 5 起每 3 行为一组关键点 (x, y, conf) × 17 组
     */
    private PoseKeypoints decodeYolo(OrtSession.Result result, int frameWidth, int frameHeight, int sourceFrame, float minConfidence) throws APIException, OrtException
    {
        Object output = result.get(0).getValue();

        if (!(output instanceof float[][][] tensor))
        {
            throw new APIException(APIException.ErrorCode.LOCAL_INFERENCE_ERROR,
                "YOLO 输出结构异常，期望 float[1][56][N]");
        }

        float[][] channels = tensor[0];
        int anchorCount = channels[0].length;

        if (channels.length < 56)
        {
            throw new APIException(APIException.ErrorCode.LOCAL_INFERENCE_ERROR,
                "YOLO 输出通道数异常：" + channels.length + "（期望 56）");
        }

        /* 收集所有目标置信度达阈值的人体 */
        List<int[]> candidates = new ArrayList<>();

        for (int anchor = 0; anchor < anchorCount; anchor++)
        {
            float score = channels[4][anchor];

            if (score >= Math.max(0.25F, minConfidence * 0.5F))
            {
                candidates.add(new int[] {anchor});
            }
        }

        if (candidates.isEmpty())
        {
            PoseKeypoints empty = new PoseKeypoints(sourceFrame);
            empty.frameConfidence = 0.0F;

            return empty;
        }

        /* 简单贪心 NMS：按置信度排序，抑制重叠框 */
        candidates.sort((a, b) -> Float.compare(channels[4][b[0]], channels[4][a[0]]));

        List<Integer> kept = new ArrayList<>();

        for (int[] candidate : candidates)
        {
            int anchor = candidate[0];
            boolean suppressed = false;

            for (int keptAnchor : kept)
            {
                if (this.iou(channels, anchor, keptAnchor) > 0.5F)
                {
                    suppressed = true;

                    break;
                }
            }

            if (!suppressed)
            {
                kept.add(anchor);

                if (kept.size() >= 5)
                {
                    break;
                }
            }
        }

        if (kept.isEmpty())
        {
            PoseKeypoints empty = new PoseKeypoints(sourceFrame);

            return empty;
        }

        /* 取保留中置信度最高者 */
        int best = kept.get(0);

        for (int anchor : kept)
        {
            if (channels[4][anchor] > channels[4][best])
            {
                best = anchor;
            }
        }

        PoseKeypoints keypoints = new PoseKeypoints(sourceFrame);

        keypoints.frameConfidence = channels[4][best];

        float scaleX = frameWidth / (float) this.model.inputWidth;
        float scaleY = frameHeight / (float) this.model.inputHeight;

        for (int k = 0; k < PoseKeypoints.KEYPOINT_COUNT; k++)
        {
            int base = 5 + k * 3;

            float x = channels[base][best] * scaleX / frameWidth;
            float y = channels[base + 1][best] * scaleY / frameHeight;
            float conf = channels[base + 2][best];

            keypoints.setPoint(k,
                Math.max(0.0F, Math.min(1.0F, x)),
                Math.max(0.0F, Math.min(1.0F, y)),
                conf);
        }

        return keypoints;
    }

    /**
     * 计算两个锚点边界框的 IoU
     */
    private float iou(float[][] channels, int a, int b)
    {
        float ax1 = channels[0][a] - channels[2][a] / 2.0F;
        float ay1 = channels[1][a] - channels[3][a] / 2.0F;
        float ax2 = channels[0][a] + channels[2][a] / 2.0F;
        float ay2 = channels[1][a] + channels[3][a] / 2.0F;

        float bx1 = channels[0][b] - channels[2][b] / 2.0F;
        float by1 = channels[1][b] - channels[3][b] / 2.0F;
        float bx2 = channels[0][b] + channels[2][b] / 2.0F;
        float by2 = channels[1][b] + channels[3][b] / 2.0F;

        float interW = Math.max(0.0F, Math.min(ax2, bx2) - Math.max(ax1, bx1));
        float interH = Math.max(0.0F, Math.min(ay2, by2) - Math.max(ay1, by1));
        float inter = interW * interH;

        float areaA = (ax2 - ax1) * (ay2 - ay1);
        float areaB = (bx2 - bx1) * (by2 - by1);

        return areaA + areaB - inter <= 0.0F ? 0.0F : inter / (areaA + areaB - inter);
    }

    /**
     * 解码 RTMPose 的 SimCC 输出：
     * simcc_x [1,17,W/2] 与 simcc_y [1,17,H/2]，对每通道取 argmax / 2 得坐标
     */
    private PoseKeypoints decodeSimCC(OrtSession.Result result, int frameWidth, int frameHeight, int sourceFrame, float minConfidence) throws APIException, OrtException
    {
        /* RTMPose 导出约定：输出顺序为 simcc_x [1,17,W/2] 与 simcc_y [1,17,H/2] */
        Object outX = result.get(0).getValue();
        Object outY = result.get(1).getValue();

        if (!(outX instanceof float[][]) || !(outY instanceof float[][]))
        {
            throw new APIException(APIException.ErrorCode.LOCAL_INFERENCE_ERROR,
                "RTMPose 输出结构异常，期望 simcc_x / simcc_y 两个浮点输出");
        }

        float[][] simccX = (float[][]) outX;
        float[][] simccY = (float[][]) outY;

        PoseKeypoints keypoints = new PoseKeypoints(sourceFrame);

        float confScale = 1.0F / Math.max(simccX[0].length, 1);

        for (int k = 0; k < PoseKeypoints.KEYPOINT_COUNT; k++)
        {
            int argmaxX = argmax(simccX[k]);
            int argmaxY = argmax(simccY[k]);

            /* SimCC 每半个像素一个 bin，除以 2 还原到输入分辨率 */
            float x = argmaxX / 2.0F / this.model.inputWidth;
            float y = argmaxY / 2.0F / this.model.inputHeight;

            /* 用峰值幅值近似置信度 */
            float conf = Math.min(1.0F, simccX[k][argmaxX] * confScale);

            keypoints.setPoint(k,
                Math.max(0.0F, Math.min(1.0F, x)),
                Math.max(0.0F, Math.min(1.0F, y)),
                conf);
        }

        keypoints.frameConfidence = keypoints.averageConfidence();

        return keypoints;
    }

    /**
     * 数组 argmax
     */
    private static int argmax(float[] array)
    {
        int index = 0;

        for (int i = 1; i < array.length; i++)
        {
            if (array[i] > array[index])
            {
                index = i;
            }
        }

        return index;
    }

    @Override
    public void close()
    {
        if (this.session != null)
        {
            try
            {
                this.session.close();
            }
            catch (Exception e)
            {
                System.err.println("[BBS AI] 释放推理会话失败：" + e.getMessage());
            }
        }
    }
}
