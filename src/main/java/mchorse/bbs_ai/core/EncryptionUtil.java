package mchorse.bbs_ai.core;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 加密工具类
 *
 * <p>负责用户 API Key 的加密存储。使用 Minecraft 用户 UUID 派生 AES 密钥，
 * 采用 AES/GCM/NoPadding 模式加密，随机 12 字节 IV 附在密文前，整体 Base64 编码。</p>
 *
 * <p>加密算法契约（必须与外部 Python 工具链保持一致）：
 * <ul>
 *   <li>密钥派生：SHA-256("bbs_ai_studio_" + minecraft_uuid) 的 32 字节摘要直接作为 AES-256 密钥</li>
 *   <li>加密模式：AES/GCM/NoPadding，GCM tag 长度 128 位</li>
 *   <li>IV：随机 12 字节，附在密文前</li>
 *   <li>编码：Base64(iv + ciphertext)</li>
 * </ul></p>
 *
 * <p>作者：BBS AI Studio</p>
 */
public class EncryptionUtil
{
    /**
     * 密钥派生前缀，与外部工具链约定的常量
     */
    public static final String KEY_PREFIX = "bbs_ai_studio_";

    /**
     * GCM IV 长度（字节）
     */
    public static final int IV_LENGTH = 12;

    /**
     * GCM 认证标签长度（位）
     */
    public static final int TAG_LENGTH_BITS = 128;

    /**
     * 安全随机数生成器（线程安全）
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    private EncryptionUtil()
    {}

    /**
     * 根据用户 UUID 派生 AES-256 密钥
     *
     * @param uuid Minecraft 用户 UUID 字符串（含连字符或不含均可，只需与写入端一致）
     * @return AES 密钥
     */
    public static SecretKey deriveKey(String uuid)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest((KEY_PREFIX + uuid).getBytes(StandardCharsets.UTF_8));

            return new SecretKeySpec(keyBytes, "AES");
        }
        catch (Exception e)
        {
            /* 理论上 SHA-256 与 AES 一定存在，这里兜底避免调用方空指针 */
            throw new RuntimeException("无法派生 AES 加密密钥", e);
        }
    }

    /**
     * 加密明文字符串
     *
     * @param plainText 明文
     * @param uuid      用户 UUID，用于派生密钥
     * @return Base64(iv + ciphertext)，失败时抛出异常
     */
    public static String encrypt(String plainText, String uuid)
    {
        if (plainText == null)
        {
            plainText = "";
        }

        try
        {
            SecretKey key = deriveKey(uuid);
            byte[] iv = new byte[IV_LENGTH];

            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);

            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            /* 拼接 IV 与密文后整体 Base64 */
            byte[] result = new byte[iv.length + cipherText.length];

            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(cipherText, 0, result, iv.length, cipherText.length);

            return Base64.getEncoder().encodeToString(result);
        }
        catch (Exception e)
        {
            throw new RuntimeException("加密配置数据失败：" + e.getMessage(), e);
        }
    }

    /**
     * 解密密文字符串
     *
     * @param encrypted Base64(iv + ciphertext)
     * @param uuid      用户 UUID，用于派生密钥
     * @return 解密后的明文；数据被篡改或密钥不匹配时抛出异常
     */
    public static String decrypt(String encrypted, String uuid)
    {
        if (encrypted == null || encrypted.isEmpty())
        {
            return "";
        }

        try
        {
            byte[] data = Base64.getDecoder().decode(encrypted);

            if (data.length <= IV_LENGTH)
            {
                throw new IllegalArgumentException("加密数据长度不合法");
            }

            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[data.length - IV_LENGTH];

            System.arraycopy(data, 0, iv, 0, IV_LENGTH);
            System.arraycopy(data, IV_LENGTH, cipherText, 0, cipherText.length);

            SecretKey key = deriveKey(uuid);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH_BITS, iv);

            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        }
        catch (Exception e)
        {
            throw new RuntimeException("解密配置数据失败（密钥不匹配或数据损坏）：" + e.getMessage(), e);
        }
    }
}
