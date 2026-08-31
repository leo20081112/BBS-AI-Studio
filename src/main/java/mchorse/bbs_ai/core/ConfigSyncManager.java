package mchorse.bbs_ai.core;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 配置同步管理器
 *
 * <p>负责 AI 配置（{@link AIConfig}）的读写持久化。配置文件位于
 * {@code config/bbs/settings/ai_providers.json}，其中敏感字段（API Key 所在的完整配置载荷）
 * 使用 {@link EncryptionUtil} 的 AES-GCM 加密存储，禁止明文落盘。</p>
 *
 * <p>配置共享契约：Java 端加密写入，Python 端（外部工具链）使用相同的
 * UUID 派生算法解密读取，确保外部工具能共用同一份配置。</p>
 *
 * <p>文件结构：
 * <pre>{@code
 * {
 *   "format": "bbs_ai_studio_config_v1",
 *   "encrypted": true,
 *   "payload": "<Base64(iv + AES-GCM(内部 JSON)）>",
 *   "updated_at": "2026-08-31T08:00:00Z"
 * }
 * }</pre>
 * 内部 JSON 为 AIConfig 的 MapType 序列化结果。</p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class ConfigSyncManager
{
    /**
     * 配置文件格式标识
     */
    public static final String FORMAT = "bbs_ai_studio_config_v1";

    /**
     * 配置文件
     */
    private static final File CONFIG_FILE = new File(BBSMod.getSettingsFolder(), "ai_providers.json");

    /**
     * 无头环境（专用服务器）下的 UUID 回退值，首次生成后持久化在配置文件外层
     */
    private static String fallbackUuid;

    /**
     * 客户端 UUID 提供者，由客户端初始化时注入；无头环境下为 null
     */
    private static Supplier<String> uuidSupplier;

    /**
     * 读写锁，保证配置文件并发安全
     */
    private static final Object LOCK = new Object();

    private ConfigSyncManager()
    {}

    /**
     * 注入客户端 UUID 提供者【原版兼容】（由客户端初始化入口调用）
     */
    public static void setUuidSupplier(Supplier<String> supplier)
    {
        uuidSupplier = supplier;
    }

    /**
     * 获取当前用于派生密钥的 UUID：优先 Minecraft 会话 UUID，无头环境使用本地持久化回退值
     */
    private static String resolveUuid()
    {
        if (uuidSupplier != null)
        {
            try
            {
                String uuid = uuidSupplier.get();

                if (uuid != null && !uuid.isEmpty())
                {
                    return uuid;
                }
            }
            catch (Exception e)
            {
                System.err.println("[BBS AI] 获取客户端 UUID 失败，回退到本地 UUID：" + e.getMessage());
            }
        }

        if (fallbackUuid == null)
        {
            fallbackUuid = UUID.randomUUID().toString();
        }

        return fallbackUuid;
    }

    /**
     * 获取配置文件
     */
    public static File getConfigFile()
    {
        return CONFIG_FILE;
    }

    /**
     * 保存 AI 配置（加密写入）
     *
     * @param config 待保存的配置
     * @return 是否保存成功
     */
    public static boolean save(AIConfig config)
    {
        if (config == null)
        {
            return false;
        }

        synchronized (LOCK)
        {
            try
            {
                /* 1. 序列化配置为内部 JSON */
                MapType payload = new MapType();

                AIConfigCodec.toData(config, payload);

                String plainJson = DataToString.toString(payload, true);

                /* 2. AES-GCM 加密后放入外层信封 */
                MapType envelope = new MapType();

                envelope.putString("format", FORMAT);
                envelope.putBool("encrypted", true);
                envelope.putString("payload", EncryptionUtil.encrypt(plainJson, resolveUuid()));
                envelope.putString("updated_at", java.time.Instant.now().toString());

                if (fallbackUuid != null)
                {
                    envelope.putString("fallback_uuid", fallbackUuid);
                }

                CONFIG_FILE.getParentFile().mkdirs();

                return DataToString.writeSilently(CONFIG_FILE, envelope, true);
            }
            catch (Exception e)
            {
                System.err.println("[BBS AI] 保存 AI 配置失败：" + e.getMessage());
                e.printStackTrace();

                return false;
            }
        }
    }

    /**
     * 加载 AI 配置（解密读取）
     *
     * @return 配置对象；文件不存在或解密失败时返回默认配置，绝不返回 null
     */
    public static AIConfig load()
    {
        AIConfig config = new AIConfig();

        synchronized (LOCK)
        {
            if (!CONFIG_FILE.isFile())
            {
                return config;
            }

            try
            {
                BaseType raw = DataToString.read(CONFIG_FILE);

                if (!(raw instanceof MapType))
                {
                    System.err.println("[BBS AI] ai_providers.json 结构不合法，使用默认配置");

                    return config;
                }

                MapType envelope = (MapType) raw;
                String fallback = envelope.getString("fallback_uuid", "");

                if (!fallback.isEmpty())
                {
                    fallbackUuid = fallback;
                }

                if (!envelope.getBool("encrypted"))
                {
                    /* 兼容历史明文配置（理论上不存在） */
                    AIConfigCodec.fromData(config, envelope.getMap("payload", new MapType()));

                    return config;
                }

                String encrypted = envelope.getString("payload", "");

                if (encrypted.isEmpty())
                {
                    return config;
                }

                String plainJson = EncryptionUtil.decrypt(encrypted, resolveUuid());
                BaseType inner = DataToString.fromString(plainJson);

                if (inner instanceof MapType)
                {
                    AIConfigCodec.fromData(config, (MapType) inner);
                }
            }
            catch (IOException e)
            {
                System.err.println("[BBS AI] 读取 AI 配置文件失败：" + e.getMessage());
            }
            catch (Exception e)
            {
                /* 解密失败通常是 UUID 不匹配或文件损坏，回退默认配置并提示 */
                System.err.println("[BBS AI] 解密 AI 配置失败（将使用默认配置）：" + e.getMessage());
            }

            return config;
        }
    }

    /**
     * 读取配置文件原始文本（供外部工具链调试，载荷仍是密文）
     */
    public static String readRaw()
    {
        try
        {
            return IOUtils.readText(CONFIG_FILE);
        }
        catch (Exception e)
        {
            return "";
        }
    }
}
