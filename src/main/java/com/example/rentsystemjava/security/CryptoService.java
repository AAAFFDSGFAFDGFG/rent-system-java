package com.example.rentsystemjava.security;

import com.example.rentsystemjava.config.RentSystemProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * SendKey 加密服务：
 * - encrypt/decrypt：AES-256-GCM（密文格式 base64(12字节IV || 密文+TAG)），推送时解密还原明文
 * - hmacHex：HMAC-SHA256 十六进制（登录恒等比对，不可逆）
 * 密钥由部署机口令（rent-system.security.secret）派生，绝不入库。
 */
@Service
public class CryptoService {

    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private final SecretKey aesKey;
    private final byte[] hmacKeyBytes;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(RentSystemProperties props) {
        byte[] seed = props.getSecurity().getSecret().getBytes(StandardCharsets.UTF_8);
        this.aesKey = derive(seed, "aes");
        this.hmacKeyBytes = derive(seed, "hmac").getEncoded();
    }

    private static SecretKey derive(byte[] seed, String purpose) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(seed);
            digest.update(purpose.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest.digest(), "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256", e);
        }
    }

    /** AES-256-GCM 加密：base64(IV || ciphertext+TAG) */
    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(IV_BYTES + ct.length).put(iv).put(ct).array());
        } catch (Exception e) {
            throw new IllegalStateException("SendKey 加密失败", e);
        }
    }

    /** AES-256-GCM 解密（推送时还原明文 SendKey，明文只存在于内存瞬间） */
    public String decrypt(String cipherBase64) {
        try {
            byte[] all = Base64.getDecoder().decode(cipherBase64);
            byte[] iv = new byte[IV_BYTES];
            byte[] ct = new byte[all.length - IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            System.arraycopy(all, IV_BYTES, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance(AES_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("SendKey 解密失败（密钥是否变更？）", e);
        }
    }

    /** HMAC-SHA256 → 64 位十六进制（登录比对 send_key_hash） */
    public String hmacHex(String plain) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKeyBytes, "HmacSHA256"));
            byte[] out = mac.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(HEX[(b >> 4) & 0x0f]).append(HEX[b & 0x0f]);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }
}
