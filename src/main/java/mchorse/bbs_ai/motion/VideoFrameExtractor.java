package mchorse.bbs_ai.motion;

import mchorse.bbs_ai.core.api.APIException;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.utils.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 视频帧提取器
 *
 * <p>通过外部 FFmpeg 进程从视频（.mp4 / .avi / .mov / .webm）提取帧序列 PNG，
 * 并支持每 N 帧取 1 帧的降采样。选择外部 FFmpeg 而非内嵌 JavaCV，
 * 避免引入数百 MB 的原生依赖；只需用户安装 FFmpeg（或通过系统属性
 * {@code bbs_ai.ffmpeg} 指定可执行文件路径）。</p>
 *
 * <p>全部方法为阻塞式，必须在后台线程调用。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class VideoFrameExtractor
{
    /**
     * 支持的视频扩展名
     */
    private static final String[] SUPPORTED_EXTENSIONS = {"mp4", "avi", "mov", "webm", "mkv"};

    /**
     * 进程最长等待时间（秒），超长视频建议先剪辑
     */
    private static final long PROCESS_TIMEOUT_SECONDS = 600;

    /**
     * 提取结果
     */
    public static class ExtractionResult
    {
        /**
         * 源视频帧率（探测失败时默认 30）
         */
        public float sourceFps = 30.0F;

        /**
         * 源视频总帧数（探测失败时为 -1）
         */
        public int totalFrames = -1;

        /**
         * 实际提取的帧率（源帧率 / 采样间隔）
         */
        public float extractedFps = 15.0F;

        /**
         * 提取出的帧图片列表（按帧序号排序）
         */
        public List<File> frames = new ArrayList<>();
    }

    /**
     * 解析 ffmpeg stderr 中 fps 信息的模式
     */
    private static final Pattern FPS_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?) fps");

    /**
     * 判断文件是否为支持的视频格式
     */
    public static boolean isSupportedVideo(File file)
    {
        if (file == null || !file.isFile())
        {
            return false;
        }

        String extension = StringUtils.extension(file.getName());

        for (String supported : SUPPORTED_EXTENSIONS)
        {
            if (supported.equalsIgnoreCase(extension))
            {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取 FFmpeg 可执行文件路径（系统属性优先，其次 PATH）
     */
    public static String resolveFfmpeg()
    {
        return System.getProperty("bbs_ai.ffmpeg", "ffmpeg");
    }

    /**
     * 检测 FFmpeg 是否可用
     */
    public static boolean isFfmpegAvailable()
    {
        try
        {
            Process process = new ProcessBuilder(resolveFfmpeg(), "-version")
                .redirectErrorStream(true)
                .start();

            process.getInputStream().read();
            process.waitFor(10, TimeUnit.SECONDS);
            process.destroyForcibly();

            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /**
     * 探测视频元信息（帧率、帧数）
     *
     * @param video 视频文件
     */
    public static float[] probeVideo(File video) throws APIException
    {
        /* 返回 [fps, totalFrames] */
        try
        {
            ProcessBuilder builder = new ProcessBuilder(
                resolveFfmpeg(), "-i", video.getAbsolutePath(),
                "-hide_banner"
            );

            builder.redirectErrorStream(true);

            Process process = builder.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
            {
                String line;

                while ((line = reader.readLine()) != null)
                {
                    output.append(line).append('\n');
                }
            }

            process.waitFor(30, TimeUnit.SECONDS);
            process.destroyForcibly();

            String text = output.toString();
            float fps = 30.0F;
            int frames = -1;

            Matcher matcher = FPS_PATTERN.matcher(text);

            if (matcher.find())
            {
                fps = Float.parseFloat(matcher.group(1));
            }

            Matcher tbrMatcher = Pattern.compile("(\\d+(?:\\.\\d+)?) tbr").matcher(text);

            if (!matcher.find() && tbrMatcher.find())
            {
                fps = Float.parseFloat(tbrMatcher.group(1));
            }

            return new float[] {fps, frames};
        }
        catch (Exception e)
        {
            throw new APIException(APIException.ErrorCode.LOCAL_INFERENCE_ERROR, "探测视频信息失败：" + e.getMessage(), -1, e);
        }
    }

    /**
     * 提取帧序列（阻塞式，需在后台线程调用）
     *
     * @param video       输入视频
     * @param outputDir   输出目录（自动创建，建议位于 ai_cache 下）
     * @param sampleEvery 每隔 N 帧取 1 帧（1 = 全部帧）
     * @param progress    进度回调（0.0 ~ 1.0），可为 null
     * @return 提取结果（帧列表 + 帧率）
     * @throws APIException FFmpeg 不可用、视频不支持或进程失败
     */
    public static ExtractionResult extract(File video, File outputDir, int sampleEvery, Consumer<Float> progress) throws APIException
    {
        if (!isSupportedVideo(video))
        {
            throw new APIException(APIException.ErrorCode.LOCAL_INFERENCE_ERROR,
                "不支持的视频格式：" + video.getName() + "（支持 " + Arrays.toString(SUPPORTED_EXTENSIONS) + "）");
        }

        if (!isFfmpegAvailable())
        {
            throw new APIException(APIException.ErrorCode.LOCAL_INFERENCE_ERROR,
                "未找到 FFmpeg。请安装 FFmpeg 并加入 PATH，或使用 -Dbbs_ai.ffmpeg=路径 指定可执行文件");
        }

        int sample = Math.max(1, sampleEvery);

        if (progress != null)
        {
            progress.accept(0.0F);
        }

        /* 1. 探测帧率，计算输出帧率 */
        float[] probe = probeVideo(video);
        float sourceFps = probe[0] > 0 ? probe[0] : 30.0F;
        float outputFps = sourceFps / sample;

        /* 2. 清空并创建输出目录 */
        try
        {
            if (outputDir.exists())
            {
                deleteRecursively(outputDir);
            }

            Files.createDirectories(outputDir.toPath());
        }
        catch (IOException e)
        {
            throw new APIException(APIException.ErrorCode.LOCAL_INFERENCE_ERROR, "创建帧缓存目录失败：" + e.getMessage(), -1, e);
        }

        /* 3. 运行 FFmpeg 抽帧 */
        File pattern = new File(outputDir, "frame_%06d.png");

        ProcessBuilder builder = new ProcessBuilder(
            resolveFfmpeg(),
            "-i", video.getAbsolutePath(),
            "-vf", "fps=" + outputFps,
            "-q:v", "2",
            pattern.getAbsolutePath(),
            "-hide_banner", "-loglevel", "error", "-y"
        );

        builder.redirectErrorStream(true);

        try
        {
            Process process = builder.start();

            StringBuilder errors = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
            {
                String line;

                while ((line = reader.readLine()) != null)
                {
                    errors.append(line).append('\n');
                }
            }

            if (!process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            {
                process.destroyForcibly();

                throw new APIException(APIException.ErrorCode.TIMEOUT, "FFmpeg 抽帧超时（>" + PROCESS_TIMEOUT_SECONDS + " 秒）");
            }

            if (process.exitValue() != 0)
            {
                throw new APIException(APIException.ErrorCode.LOCAL_INFERENCE_ERROR,
                    "FFmpeg 抽帧失败（退出码 " + process.exitValue() + "）：" + errors);
            }
        }
        catch (APIException e)
        {
            throw e;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();

            throw new APIException(APIException.ErrorCode.CANCELLED, "抽帧被中断", -1, e);
        }
        catch (IOException e)
        {
            throw new APIException(APIException.ErrorCode.LOCAL_INFERENCE_ERROR, "无法启动 FFmpeg 进程：" + e.getMessage(), -1, e);
        }

        /* 4. 收集帧文件（按名称排序 = 按时间排序） */
        ExtractionResult result = new ExtractionResult();

        File[] files = outputDir.listFiles((dir, name) -> name.endsWith(".png"));

        if (files != null)
        {
            result.frames.addAll(Arrays.asList(files));
            result.frames.sort(Comparator.comparing(File::getName));
        }

        if (result.frames.isEmpty())
        {
            throw new APIException(APIException.ErrorCode.LOCAL_INFERENCE_ERROR, "FFmpeg 未提取出任何帧，请检查视频编码");
        }

        result.sourceFps = sourceFps;
        result.extractedFps = outputFps;
        result.totalFrames = (int) probe[1];

        if (progress != null)
        {
            progress.accept(1.0F);
        }

        return result;
    }

    /**
     * 递归删除目录
     */
    private static void deleteRecursively(File folder)
    {
        File[] files = folder.listFiles();

        if (files != null)
        {
            for (File file : files)
            {
                if (file.isDirectory())
                {
                    deleteRecursively(file);
                }
                else
                {
                    file.delete();
                }
            }
        }

        folder.delete();
    }

    /**
     * 获取游戏内帧缓存根目录 {@code config/bbs/ai_cache/frames/<视频名>}
     */
    public static File getFramesFolder(String videoName)
    {
        return new File(BBSMod.getGamePath("config/bbs/ai_cache/frames"), videoName);
    }
}
