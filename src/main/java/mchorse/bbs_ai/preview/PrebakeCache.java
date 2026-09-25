package mchorse.bbs_ai.preview;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 预烘焙内存缓存
 *
 * <p>键为 {@code 影片|角色|表单}（见 {@link PreviewContext#cacheKey()}），
 * 值为预览轨道。预览生成在后台线程写入、渲染线程读取，故使用并发哈希表。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class PrebakeCache
{
    /**
     * 缓存表
     */
    private final Map<String, PreviewTrack> cache = new ConcurrentHashMap<>();

    /**
     * 上下文表（与缓存键一一对应）
     */
    private final Map<String, PreviewContext> contexts = new ConcurrentHashMap<>();

    /**
     * 存入预览轨道
     */
    public void put(PreviewContext context, PreviewTrack track)
    {
        String key = context.cacheKey();

        this.cache.put(key, track);
        this.contexts.put(key, context);
    }

    /**
     * 取出预览轨道
     */
    public PreviewTrack get(String key)
    {
        return this.cache.get(key);
    }

    /**
     * 取出上下文
     */
    public PreviewContext getContext(String key)
    {
        return this.contexts.get(key);
    }

    /**
     * 是否存在指定键的预览
     */
    public boolean has(String key)
    {
        return this.cache.containsKey(key);
    }

    /**
     * 当前缓存条目数
     */
    public int size()
    {
        return this.cache.size();
    }

    /**
     * 全部缓存键
     */
    public java.util.Set<String> keys()
    {
        return this.cache.keySet();
    }

    /**
     * 清除指定键
     */
    public void discard(String key)
    {
        this.cache.remove(key);
        this.contexts.remove(key);
    }

    /**
     * 清空全部缓存（放弃所有预览）
     */
    public void discardAll()
    {
        this.cache.clear();
        this.contexts.clear();
    }
}
