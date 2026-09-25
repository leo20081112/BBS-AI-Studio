package mchorse.bbs_ai.core;

import mchorse.bbs_ai.BBSAIStudio;
import mchorse.bbs_ai.motion.LocalPoseEstimator;
import mchorse.bbs_ai.motion.LocalPoseEstimator.PoseModel;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.utils.FFMpegUtils;
import mchorse.bbs_mod.utils.OS;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * 本地组件下载器（FFmpeg 运行库 + ONNX 姿态模型）
 *
 * <p>进世界后检测到本地组件缺失时，经用户确认从镜像列表逐源下载，
 * 全部通过 SHA-256 校验后原子落盘到 {@code config/bbs/ai_cache/}：
 * <ul>
 *   <li>FFmpeg —— 单文件静态构建（gzip 压缩），装到 {@code ai_cache/bin/ffmpeg/}，
 *       完成后自动写入 {@code -Dbbs_ai.ffmpeg} 与 BBS 编码器路径设置</li>
 *   <li>yolov8n-pose.onnx / rtmpose-m.onnx —— 装到 {@code ai_cache/models/}，
 *       即 {@link LocalPoseEstimator} 的解析目录</li>
 * </ul></p>
 *
 * <p>所有网络与进程探测均为阻塞式，只允许在后台线程调用 {@link #findMissing()} 与
 * {@link #download(List, DownloadJob)}；UI 通过轮询 {@link DownloadJob} 的易变字段刷新。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class LocalComponentsDownloader
{
    /**
     * 下载源（GitHub Release 资产 / Hugging Face LFS）的固定版本清单；
     * 哈希为发布时实测的最终文件（解压后）SHA-256
     */
    private static final String FFMPEG_BASE = "https://github.com/eugeneware/ffmpeg-static/releases/download/b6.0/";

    private static final String YOLO_REPO = "Xenova/yolov8n-pose";

    private static final String YOLO_REMOTE = "onnx/model.onnx";

    private static final String RTM_REPO = "bukuroo/RTMPose-ONNX";

    private static final String RTM_REMOTE = "rtmpose-m.onnx";

    private static final String[] MODEL_MIRRORS = {"https://huggingface.co/", "https://hf-mirror.com/"};

    /**
     * 一个待安装组件的描述（目标文件、镜像列表、期望大小与哈希）
     */
    public static class Component
    {
        /**
         * 展示名称（用于进度行）
         */
        public final String title;

        /**
         * 安装目标文件
         */
        public final File target;

        /**
         * 镜像下载地址（依次尝试）
         */
        public final String[] urls;

        /**
         * 最终文件的期望 SHA-256（十六进制小写）
         */
        public final String sha256;

        /**
         * 下载字节数（gzip 的为压缩包大小），用于进度分母
         */
        public final long downloadSize;

        /**
         * 下载内容是否为 gzip 压缩的单文件
         */
        public final boolean gunzip;

        public Component(String title, File target, String[] urls, String sha256, long downloadSize, boolean gunzip)
        {
            this.title = title;
            this.target = target;
            this.urls = urls;
            this.sha256 = sha256;
            this.downloadSize = downloadSize;
            this.gunzip = gunzip;
        }
    }

    /**
     * 一次下载任务的共享进度状态（UI 渲染线程轮询读取）
     */
    public static class DownloadJob
    {
        /**
         * 当前正在处理的组件名
         */
        public volatile String currentTitle = "";

        /**
         * 人类可读的进度行（如 "12.3 MB / 28.9 MB（42%）"）
         */
        public volatile String statusLine = "";

        /**
         * 总体进度 0~100
         */
        public volatile int percent = 0;

        /**
         * 全部组件是否处理完毕
         */
        public volatile boolean finished = false;

        /**
         * 全部组件是否安装成功
         */
        public volatile boolean success = false;

        /**
         * 失败原因（成功时为 null）
         */
        public volatile String error = null;
    }

    /**
     * 获取受管的 FFmpeg 目录 {@code config/bbs/ai_cache/bin/ffmpeg/}
     */
    public static File getFFmpegFolder()
    {
        return BBSAIStudio.getAICachePath("bin/ffmpeg");
    }

    /**
     * 获取受管 FFmpeg 可执行文件的期望位置
     */
    public static File getManagedFFmpeg()
    {
        return new File(getFFmpegFolder(), OS.CURRENT == OS.WINDOWS ? "ffmpeg.exe" : "ffmpeg");
    }

    /**
     * 受管 FFmpeg 是否已安装（仅存在性判断，哈希在安装时已校验）
     */
    public static boolean isManagedFFmpegInstalled()
    {
        File ffmpeg = getManagedFFmpeg();

        return ffmpeg.isFile() && ffmpeg.length() > 0L;
    }

    /**
     * 当前环境（受管副本 / PATH / 设置路径 / 系统属性）里 FFmpeg 是否可直接运行
     *
     * <p>会启动子进程探测，务必在后台线程调用。</p>
     */
    public static boolean isFFmpegUsable()
    {
        if (isManagedFFmpegInstalled())
        {
            return true;
        }

        String property = System.getProperty("bbs_ai.ffmpeg");

        if (property != null && !property.isEmpty() && probeFFmpeg(property))
        {
            return true;
        }

        if (probeFFmpeg("ffmpeg"))
        {
            return true;
        }

        return FFMpegUtils.version().isPresent();
    }

    /**
     * 运行 {@code <命令> -version} 并检查退出码
     */
    private static boolean probeFFmpeg(String command)
    {
        try
        {
            Process process = new ProcessBuilder(command, "-version")
                .redirectErrorStream(true)
                .start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
            {
                while (reader.readLine() != null)
                {}
            }

            boolean exited = process.waitFor(15, TimeUnit.SECONDS);

            process.destroyForcibly();

            return exited && process.exitValue() == 0;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    /**
     * 按当前系统与架构挑一个实测过的 FFmpeg 静态构建；不支持的平台返回 null
     */
    public static Component createFFmpegComponent()
    {
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean arm = arch.contains("aarch64") || arch.contains("arm");

        String variant;
        String fileName;

        if (OS.CURRENT == OS.WINDOWS && !arm)
        {
            variant = "ffmpeg-win32-x64.gz";
            fileName = "ffmpeg.exe";
        }
        else if (OS.CURRENT == OS.LINUX && !arm)
        {
            variant = "ffmpeg-linux-x64.gz";
            fileName = "ffmpeg";
        }
        else if (OS.CURRENT == OS.MACOS)
        {
            variant = arm ? "ffmpeg-darwin-arm64.gz" : "ffmpeg-darwin-x64.gz";
            fileName = "ffmpeg";
        }
        else
        {
            return null;
        }

        String url = FFMPEG_BASE + variant;

        if (variant.equals("ffmpeg-win32-x64.gz"))
        {
            return new Component("FFmpeg", new File(getFFmpegFolder(), fileName),
                new String[] {url},
                "450d66226c79405c724e821f291cab0911e934bfa9fa2231adcab587f3e07b50",
                28894894L, true);
        }
        else if (variant.equals("ffmpeg-linux-x64.gz"))
        {
            return new Component("FFmpeg", new File(getFFmpegFolder(), fileName),
                new String[] {url},
                "17c1ae10b52ac499180679fe6ba77e17642390c4eedb0f1e3b0ac045da55128f",
                28928723L, true);
        }
        else if (variant.equals("ffmpeg-darwin-x64.gz"))
        {
            return new Component("FFmpeg", new File(getFFmpegFolder(), fileName),
                new String[] {url},
                "a12354fce7eb62361473bbe10d53a1893695babd35869ec8e92e5dfea8d0440b",
                25067704L, true);
        }

        return new Component("FFmpeg", new File(getFFmpegFolder(), fileName),
            new String[] {url},
            "6be74d6f449889c2e87a75873894f8520cad56c08ac76f2a628d85b0519daaca",
            19253088L, true);
    }

    /**
     * 构建姿态模型组件（Hugging Face 主源 + hf-mirror 镜像源）
     */
    public static Component createModelComponent(PoseModel model)
    {
        String[] urls = new String[MODEL_MIRRORS.length];

        if (model == PoseModel.RTMPOSE)
        {
            for (int i = 0; i < MODEL_MIRRORS.length; i++)
            {
                urls[i] = MODEL_MIRRORS[i] + RTM_REPO + "/resolve/main/" + RTM_REMOTE;
            }

            return new Component(model.fileName, new File(LocalPoseEstimator.getModelsFolder(), model.fileName),
                urls,
                "8ed9cd4724582d00e7978c297472365946277185ecc448d4ad47f4f376ae3208",
                54330877L, false);
        }

        for (int i = 0; i < MODEL_MIRRORS.length; i++)
        {
            urls[i] = MODEL_MIRRORS[i] + YOLO_REPO + "/resolve/main/" + YOLO_REMOTE;
        }

        return new Component(model.fileName, new File(LocalPoseEstimator.getModelsFolder(), model.fileName),
            urls,
            "04f6d2416266f2aba6c5ba8b26de33ed9eba3279f972ac02e33f9f1366547586",
            13484153L, false);
    }

    /**
     * 找出当前缺失且可自动安装的组件（FFmpeg + 当前选择的姿态模型）
     *
     * <p>会启动子进程探测 FFmpeg，务必在后台线程调用。</p>
     */
    public static List<Component> findMissing()
    {
        List<Component> missing = new ArrayList<>();

        if (!isFFmpegUsable())
        {
            Component ffmpeg = createFFmpegComponent();

            if (ffmpeg != null)
            {
                missing.add(ffmpeg);
            }
        }

        PoseModel model = selectedPoseModel();

        if (model != null)
        {
            File file = new File(LocalPoseEstimator.getModelsFolder(), model.fileName);

            if (!file.isFile())
            {
                missing.add(createModelComponent(model));
            }
        }

        return missing;
    }

    /**
     * 读取设置里当前选择的姿态模型
     */
    private static PoseModel selectedPoseModel()
    {
        int index = BBSAISettings.motionPoseModel == null ? 0 : BBSAISettings.motionPoseModel.get();
        PoseModel[] models = PoseModel.values();

        return models[Math.max(0, Math.min(models.length - 1, index))];
    }

    /**
     * 在新的后台守护线程里依次下载安装全部组件（组件串行，避免抢占带宽）
     *
     * @return 供 UI 轮询进度的任务对象
     */
    public static DownloadJob download(List<Component> components)
    {
        DownloadJob job = new DownloadJob();
        List<Component> items = new ArrayList<>(components);

        Thread thread = new Thread(() -> runJob(items, job), "BBS AI 本地组件下载");

        thread.setDaemon(true);
        thread.start();

        return job;
    }

    /**
     * 任务主体（下载线程内执行）
     */
    private static void runJob(List<Component> components, DownloadJob job)
    {
        int total = components.size();
        int failed = 0;
        StringBuilder errors = new StringBuilder();

        for (int i = 0; i < total; i++)
        {
            final int index = i;
            Component component = components.get(i);

            job.currentTitle = component.title;
            job.statusLine = "0 / " + formatSize(component.downloadSize);

            String error = installComponent(component, (received, size) ->
            {
                int percent = size <= 0L ? 0 : (int) Math.min(100L, received * 100L / size);

                job.statusLine = formatSize(received) + " / " + formatSize(size) + "（" + percent + "%）";
                job.percent = Math.min(99, (index * 100 + percent) / total);
            });

            if (error == null)
            {
                applyComponent(component);
            }
            else
            {
                failed++;

                if (errors.length() > 0)
                {
                    errors.append("；");
                }

                errors.append(component.title).append("：").append(error);
            }
        }

        job.percent = 100;
        job.finished = true;
        job.success = failed == 0;
        job.error = failed == 0 ? null : errors.toString();
    }

    /**
     * 组件安装成功后的收尾：FFmpeg 写回运行配置
     */
    private static void applyComponent(Component component)
    {
        if (component.target.equals(getManagedFFmpeg()))
        {
            File ffmpeg = component.target;

            System.setProperty("bbs_ai.ffmpeg", ffmpeg.getAbsolutePath());

            if (BBSSettings.videoEncoderPath != null)
            {
                BBSSettings.videoEncoderPath.set(ffmpeg.getAbsolutePath());
            }
        }
    }

    /**
     * 安装单个组件：逐镜像下载到 .part 临时文件，SHA-256 通过后落盘
     *
     * @param progress 进度回调（下载线程内调用，received/size 为字节）
     * @return 失败原因，成功返回 null
     */
    private static String installComponent(Component component, ProgressConsumer progress)
    {
        File target = component.target;

        target.getParentFile().mkdirs();

        File part = new File(target.getParentFile(), target.getName() + ".part");

        String lastError = "没有可用的下载源";

        for (String url : component.urls)
        {
            try
            {
                downloadFile(url, part, component, progress);

                String hash = sha256(part);

                if (!hash.equalsIgnoreCase(component.sha256))
                {
                    part.delete();

                    lastError = "SHA-256 校验失败（" + shortHash(hash) + "）";

                    continue;
                }

                if (component.gunzip)
                {
                    gunzip(part, target);
                    part.delete();
                }
                else
                {
                    moveFile(part, target);
                }

                if (OS.CURRENT != OS.WINDOWS)
                {
                    target.setExecutable(true, false);
                }

                return null;
            }
            catch (Exception e)
            {
                part.delete();

                lastError = e.getMessage() == null ? e.toString() : e.getMessage();
            }
        }

        return lastError;
    }

    /**
     * 单源下载（带 UA、30 秒读超时与流式 SHA-256/进度统计）
     */
    private static void downloadFile(String url, File part, Component component, ProgressConsumer progress) throws IOException
    {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "BBS-FS-AI/" + BBSAIStudio.VERSION + " (Minecraft mod)");

        int code = connection.getResponseCode();

        if (code < 200 || code >= 300)
        {
            connection.disconnect();

            throw new IOException("HTTP " + code);
        }

        long total = connection.getContentLengthLong();

        if (total <= 0L)
        {
            total = component.downloadSize;
        }

        MessageDigest digest;

        try
        {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (Exception e)
        {
            throw new IOException("SHA-256 不可用");
        }

        long received = 0L;
        byte[] buffer = new byte[16384];
        long lastReport = System.currentTimeMillis();

        try (java.io.InputStream input = connection.getInputStream())
        {
            try (java.io.OutputStream output = Files.newOutputStream(part.toPath()))
            {
                int read;

                while ((read = input.read(buffer)) >= 0)
                {
                    output.write(buffer, 0, read);
                    digest.update(buffer, 0, read);
                    received += read;

                    long now = System.currentTimeMillis();

                    if (now - lastReport >= 200L)
                    {
                        lastReport = now;
                        progress.accept(received, total);
                    }
                }
            }
        }
        finally
        {
            connection.disconnect();
        }

        progress.accept(received, total);
    }

    /**
     * gzip 解压单个文件
     */
    private static void gunzip(File source, File target) throws IOException
    {
        File temp = new File(target.getParentFile(), target.getName() + ".unzip");

        try (GZIPInputStream input = new GZIPInputStream(Files.newInputStream(source.toPath()), 65536))
        {
            try (java.io.OutputStream output = Files.newOutputStream(temp.toPath()))
            {
                byte[] buffer = new byte[65536];
                int read;

                while ((read = input.read(buffer)) >= 0)
                {
                    output.write(buffer, 0, read);
                }
            }
        }

        moveFile(temp, target);
    }

    /**
     * 原子优先的文件移动
     */
    private static void moveFile(File source, File target) throws IOException
    {
        try
        {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (IOException e)
        {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 计算文件 SHA-256（十六进制小写）
     */
    private static String sha256(File file) throws IOException
    {
        MessageDigest digest;

        try
        {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (Exception e)
        {
            throw new IOException("SHA-256 不可用");
        }

        try (java.io.InputStream input = Files.newInputStream(file.toPath()))
        {
            byte[] buffer = new byte[65536];
            int read;

            while ((read = input.read(buffer)) >= 0)
            {
                digest.update(buffer, 0, read);
            }
        }

        StringBuilder builder = new StringBuilder();

        for (byte value : digest.digest())
        {
            builder.append(Character.forDigit((value >> 4) & 0xF, 16)).append(Character.forDigit(value & 0xF, 16));
        }

        return builder.toString();
    }

    /**
     * 哈希截断展示（错误信息用）
     */
    private static String shortHash(String hash)
    {
        return hash.length() <= 12 ? hash : hash.substring(0, 12) + "…";
    }

    /**
     * 字节数格式化（MB 保留一位小数）
     */
    public static String formatSize(long bytes)
    {
        return String.format("%.1f MB", bytes / 1024.0D / 1024.0D);
    }

    /**
     * 下载进度回调（字节）
     */
    private interface ProgressConsumer
    {
        void accept(long received, long total);
    }
}
