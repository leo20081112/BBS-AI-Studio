package mchorse.bbs_ai.ui.language;

import mchorse.bbs_ai.core.BBSAISettings;
import mchorse.bbs_ai.integration.event.LanguageChangeEvent;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 语言管理器（客户端，单例）
 *
 * <p>支持 English / 简体中文 / 繁體中文 三语切换：
 * <ul>
 *   <li>语言文件经 {@code RegisterL10nEvent} 注册进 BBS 的 L10n 系统【原版兼容】</li>
 *   <li>{@link #setLanguage(UILanguage)} 切换后调用 BBS 的语言重载，
 *       所有 {@code L10n.lang(key)} 标签即时刷新</li>
 *   <li>切换完成后发布 {@link LanguageChangeEvent}（供 addon 扩展）</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class LanguageManager
{
    /**
     * 单例
     */
    private static LanguageManager instance;

    /**
     * 当前语言
     */
    private UILanguage language = UILanguage.ENGLISH;

    /**
     * 语言变更监听器
     */
    private final List<Consumer<UILanguage>> listeners = new ArrayList<>();

    /**
     * 初始化（从设置读取语言）
     */
    public static synchronized void initialize()
    {
        if (instance == null)
        {
            instance = new LanguageManager();
            instance.language = UILanguage.byIndex(BBSAISettings.uiLanguage.get());
        }
    }

    /**
     * 获取单例
     */
    public static LanguageManager get()
    {
        if (instance == null)
        {
            initialize();
        }

        return instance;
    }

    /**
     * 订阅语言变更
     */
    public void addListener(Consumer<UILanguage> listener)
    {
        this.listeners.add(listener);
    }

    /**
     * 获取当前语言
     */
    public UILanguage getLanguage()
    {
        return this.language;
    }

    /**
     * 切换语言（即时生效）
     */
    public void setLanguage(UILanguage newLanguage)
    {
        if (newLanguage == null)
        {
            newLanguage = UILanguage.ENGLISH;
        }

        UILanguage old = this.language;

        if (old == newLanguage)
        {
            return;
        }

        this.language = newLanguage;

        /* 持久化设置 */
        if (BBSAISettings.uiLanguage != null)
        {
            BBSAISettings.uiLanguage.set(newLanguage.ordinal());
        }

        /* 重载 BBS 语言系统（含我们注册的语言文件）【原版兼容】 */
        try
        {
            BBSModClient.reloadLanguage(newLanguage.code);
        }
        catch (Exception e)
        {
            System.err.println("[BBS AI] 语言重载失败：" + e.getMessage());
        }

        /* 通知监听器 */
        for (Consumer<UILanguage> listener : this.listeners)
        {
            try
            {
                listener.accept(newLanguage);
            }
            catch (Exception e)
            {
                System.err.println("[BBS AI] 语言监听器异常：" + e.getMessage());
            }
        }

        /* 发布语言变更事件 */
        BBSMod.events.post(new LanguageChangeEvent(newLanguage, old));
    }
}
