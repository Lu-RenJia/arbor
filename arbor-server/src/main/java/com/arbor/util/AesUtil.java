package com.arbor.util;

import com.arbor.common.ErrorCode;
import com.arbor.common.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES 加解密工具 - 用于数据源密码加密存储
 * 采用 AES/CBC/PKCS5Padding 模式，每次加密使用随机 IV
 * IV（16字节）拼接在密文前面：Base64(IV + ciphertext)
 *
 * @author gwl
 */
@Slf4j
@Component
public class AesUtil {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16;

    private final byte[] secretKey;

    public AesUtil(@Value("${aes.secret-key}") String key) {
        // 启动期校验：密钥必须由环境变量注入，缺失时 fail-fast（避免静默用空密钥加密）
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "aes.secret-key 未配置：请通过环境变量 AES_SECRET_KEY 注入（16 字节），禁止硬编码到代码仓库");
        }
        byte[] raw = key.getBytes(StandardCharsets.UTF_8);
        if (raw.length != IV_LENGTH) {
            log.warn("AES 密钥长度为 {} 字节，非标准 16 字节，将被截断/补齐；建议配置恰好 16 字节的密钥", raw.length);
        }
        // 确保密钥恰好 16 字节（128位）
        this.secretKey = Arrays.copyOf(raw, IV_LENGTH);
    }

    /**
     * AES 加密
     * @param plainText 明文
     * @return Base64(IV + 密文)
     */
    public String encrypt(String plainText) {
        try {
            // 生成随机 IV
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            SecretKeySpec keySpec = new SecretKeySpec(secretKey, "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // IV + 密文 拼接后 Base64 编码
            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("AES加密失败", e);
            throw new SystemException(ErrorCode.ENCRYPT_FAILED, e);
        }
    }

    /**
     * AES 解密
     * @param encryptedText Base64(IV + 密文)
     * @return 明文
     */
    public String decrypt(String encryptedText) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);

            // 提取 IV（前16字节）
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            // 提取密文（剩余部分）
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_LENGTH, combined.length);

            SecretKeySpec keySpec = new SecretKeySpec(secretKey, "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decrypted = cipher.doFinal(ciphertext);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("AES解密失败", e);
            throw new SystemException(ErrorCode.DECRYPT_FAILED, e);
        }
    }
}
