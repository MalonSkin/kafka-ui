package com.provectus.kafka.ui.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM 字符串加密转换器。
 * <p>
 * 对数据库中的敏感字段（如密码）进行加密存储，读取时自动解密。
 * 密钥通过环境变量 {@code KAFKA_UI_DB_ENCRYPTION_KEY}（Base64 编码的 256-bit 密钥）提供。
 * 加密结果格式：Base64(IV + ciphertext + GCM tag)，其中 IV 为 12 字节随机值。
 * </p>
 * <p>
 * 向后兼容：
 * <ul>
 *   <li>密钥未设置时，跳过加密/解密，明文直通</li>
 *   <li>解密失败（如数据为明文）时，原样返回数据库中的值</li>
 * </ul>
 * </p>
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    /** AES/GCM/NoPadding 算法 */
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    /** GCM 认证标签长度（128 bit） */
    private static final int GCM_TAG_LENGTH = 128;

    /** 初始化向量（IV）长度：12 字节 */
    private static final int IV_LENGTH = 12;

    /** 安全随机数生成器 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 加密密钥（从环境变量加载，未设置时为 null） */
    private static final SecretKey SECRET_KEY = loadSecretKey();

    /**
     * 从环境变量加载 AES-256 密钥。
     *
     * @return 密钥对象，环境变量未设置时返回 null
     */
    private static SecretKey loadSecretKey() {
        String encodedKey = System.getenv("KAFKA_UI_DB_ENCRYPTION_KEY");
        if (encodedKey == null || encodedKey.isBlank()) {
            return null;
        }
        byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
        return new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        // 值为空时直接返回
        if (attribute == null) {
            return null;
        }
        // 密钥未设置时跳过加密（向后兼容）
        if (SECRET_KEY == null) {
            return attribute;
        }
        try {
            // 生成 12 字节随机 IV
            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            // 执行 AES-GCM 加密
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY, spec);
            byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            // 拼接 IV + ciphertext，然后 Base64 编码
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            throw new IllegalStateException("AES-GCM 加密失败", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        // 值为空时直接返回
        if (dbData == null) {
            return null;
        }
        // 密钥未设置时跳过解密（向后兼容）
        if (SECRET_KEY == null) {
            return dbData;
        }
        try {
            // Base64 解码
            byte[] decoded = Base64.getDecoder().decode(dbData);

            // 分离 IV 和密文
            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[decoded.length - IV_LENGTH];
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            buffer.get(iv);
            buffer.get(ciphertext);

            // 执行 AES-GCM 解密
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY, spec);
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // 解密失败（如数据为明文），原样返回（向后兼容）
            return dbData;
        }
    }
}
