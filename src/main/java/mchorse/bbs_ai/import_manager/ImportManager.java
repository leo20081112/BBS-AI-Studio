package mchorse.bbs_ai.import_manager;

import mchorse.bbs_ai.BBSAIStudio;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 统一导入管理器
 *
 * <p>扫描两个目录并汇总为可导入条目：
 * <ul>
 *   <li>{@code config/bbs/ai_cache/} —— 游戏内 AI 生成缓存（SourceType.INTERNAL_AI）</li>
 *   <li>{@code config/bbs/imports/} —— 外部工具输出（SourceType.EXTERNAL_TOOL）</li>
 * </ul>
 * 支持手动刷新与 {@link WatchService} 自动监听（可在设置中关闭）。
 * 监听线程为守护线程，回调统一投递到监听线程，UI 侧自行切换主线程。</p>
 *
 * <p>接口契约：文件扫描结果按修改时间倒序；所有列表均为拷贝，调用方持有快照。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ImportManager
{
    /**
     * 单例
     */
    private static ImportManager instance;

    /**
     * 条目变更监听器（刷新 / 自动监听触发时回调）
     */
    private final List<Consumer<List<ImportEntry>>> listeners = new CopyOnWriteArrayList<>();

    /**
     * 最近一次扫描结果
     */
    private volatile List<ImportEntry> entries = new ArrayList<>();

    /**
     * 文件监听服务与线程
     */
    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean watching;

    /**
     * 初始化（在整合初始化中调用，启动目录监听）
     */
    public static synchronized void initialize()
    {
        if (instance == null)
        {
            instance = new ImportManager();
        }

        instance.scanAllSources();
    }

    /**
     * 获取单例
     */
    public static ImportManager get()
    {
        if (instance == null)
        {
            initialize();
        }

        return instance;
    }

    /**
     * 订阅条目变更（回调在监听 / 扫描线程，UI 需自行调度到主线程）
     */
    public void addListener(Consumer<List<ImportEntry>> listener)
    {
        if (listener != null)
        {
            this.listeners.add(listener);
        }
    }

    /**
     * 移除监听器
     */
    public void removeListener(Consumer<List<ImportEntry>> listener)
    {
        this.listeners.remove(listener);
    }

    /**
     * 扫描全部来源目录
     *
     * @return 条目快照（按修改时间倒序）
     */
    public List<ImportEntry> scanAllSources()
    {
        List<ImportEntry> scanned = new ArrayList<>();

        scanned.addAll(this.scanFolder(BBSAIStudio.getAICacheFolder(), SourceType.INTERNAL_AI));
        scanned.addAll(this.scanFolder(BBSAIStudio.getImportsFolder(), SourceType.EXTERNAL_TOOL));

        scanned.sort(Comparator.comparingLong((ImportEntry entry) -> entry.lastModified).reversed());

        this.entries = scanned;

        for (Consumer<List<ImportEntry>> listener : this.listeners)
        {
            try
            {
                listener.accept(scanned);
            }
            catch (Exception e)
            {
                System.err.println("[BBS AI] 导入监听回调异常：" + e.getMessage());
            }
        }

        return scanned;
    }

    /**
     * 扫描单个目录中的 .json 动作文件
     */
    private List<ImportEntry> scanFolder(File folder, SourceType source)
    {
        List<ImportEntry> result = new ArrayList<>();

        if (!folder.isDirectory())
        {
            folder.mkdirs();

            return result;
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));

        if (files == null)
        {
            return result;
        }

        for (File file : files)
        {
            /* 跳过 models 子目录之外的非常规文件（0 字节等） */
            if (file.isFile() && file.length() > 0)
            {
                result.add(new ImportEntry(source, file));
            }
        }

        return result;
    }

    /**
     * 获取最近一次扫描结果（快照）
     */
    public List<ImportEntry> getEntries()
    {
        return new ArrayList<>(this.entries);
    }

    /**
     * 启动目录自动监听（幂等；受设置 import.watch_enabled 控制）
     */
    public synchronized void startWatching()
    {
        if (this.watching)
        {
            return;
        }

        try
        {
            this.watchService = FileSystems.getDefault().newWatchService();
            this.watching = true;

            Path cachePath = BBSAIStudio.getAICacheFolder().toPath();
            Path importsPath = BBSAIStudio.getImportsFolder().toPath();

            cachePath.register(this.watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            importsPath.register(this.watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);

            this.watchThread = new Thread(this::watchLoop, "BBS-AI-Import-Watch");
            this.watchThread.setDaemon(true);
            this.watchThread.start();

            System.out.println("[BBS AI] 导入目录自动监听已启动");
        }
        catch (IOException e)
        {
            this.watching = false;
            System.err.println("[BBS AI] 启动目录监听失败（自动刷新不可用，可手动刷新）：" + e.getMessage());
        }
    }

    /**
     * 停止目录监听
     */
    public synchronized void stopWatching()
    {
        this.watching = false;

        if (this.watchThread != null)
        {
            this.watchThread.interrupt();
            this.watchThread = null;
        }

        if (this.watchService != null)
        {
            try
            {
                this.watchService.close();
            }
            catch (IOException ignored)
            {}

            this.watchService = null;
        }
    }

    /**
     * 监听循环（防抖 500 毫秒批量刷新）
     */
    private void watchLoop()
    {
        while (this.watching)
        {
            try
            {
                WatchKey key = this.watchService.take();

                Thread.sleep(500);

                boolean relevant = false;

                for (WatchEvent<?> event : key.pollEvents())
                {
                    Object context = event.context();

                    if (context instanceof java.nio.file.Path changed
                        && changed.toString().toLowerCase().endsWith(".json"))
                    {
                        relevant = true;
                    }
                }

                if (relevant)
                {
                    this.scanAllSources();
                }

                if (!key.reset())
                {
                    break;
                }
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();

                break;
            }
            catch (Exception e)
            {
                System.err.println("[BBS AI] 目录监听异常：" + e.getMessage());

                try
                {
                    Thread.sleep(2000);
                }
                catch (InterruptedException interrupted)
                {
                    break;
                }
            }
        }
    }

    /**
     * 关闭（游戏退出时调用）
     */
    public static void shutdown()
    {
        if (instance != null)
        {
            instance.stopWatching();
        }
    }
}
