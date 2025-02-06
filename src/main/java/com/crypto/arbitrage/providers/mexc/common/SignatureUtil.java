package com.crypto.arbitrage.providers.mexc.common;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class SignatureUtil {

    /**
     * Создаёт подпись HMAC SHA256 на основе строки параметров.
     *
     * @param apiSecret     Ваш секретный ключ API.
     * @param rawQueryString Строка параметров без URL-кодирования.
     * @return Подпись в виде шестнадцатеричной строки.
     */
    public static String createSignature(String apiSecret, String rawQueryString) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256");
            sha256_HMAC.init(secretKey);
            byte[] hash = sha256_HMAC.doFinal(rawQueryString.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Не удалось создать подпись", e);
        }
    }

    /**
     * Преобразует массив байтов в шестнадцатеричную строку.
     *
     * @param bytes Массив байтов.
     * @return Шестнадцатеричная строка.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
