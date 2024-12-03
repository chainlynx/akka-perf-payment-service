package payment.demo.perfTests;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class HashUtils {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private HashUtils() {
        // Private constructor to prevent instantiation
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Exception class for handling hashing-related errors
     */
    public static class HashingException extends RuntimeException {
        public HashingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Encodes multiple values into a single encrypted hash using a seed phrase.
     * Returns a URL-safe string.
     * @param seedPhrase The phrase used to generate the encryption key
     * @param values The values to encode
     * @return URL-safe Base64 encoded encrypted string
     */
    public static String encode(String seedPhrase, Object... values) {
        try {
            SecretKey key = generateKey(seedPhrase);

            // Convert values to string and join with delimiter
            String combined = joinValues(values);

            // Generate a random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            // Initialize cipher for encryption
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

            // Encrypt the data
            byte[] encrypted = cipher.doFinal(combined.getBytes(StandardCharsets.UTF_8));

            // Combine IV and encrypted data
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encrypted.length);
            byteBuffer.put(iv);
            byteBuffer.put(encrypted);

            // Encode as URL-safe Base64 string
            return Base64.getUrlEncoder().withoutPadding().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new HashingException("Failed to encode values", e);
        }
    }

    /**
     * Decodes an encrypted hash back into an array of values using the same seed phrase
     * @param seedPhrase The phrase used to generate the decryption key
     * @param hash The URL-safe encrypted hash to decode
     * @return Array of decoded values as strings
     */
    public static String[] decode(String seedPhrase, String hash) {
        try {
            SecretKey key = generateKey(seedPhrase);

            // Decode URL-safe Base64 string
            byte[] decoded = Base64.getUrlDecoder().decode(hash);

            // Extract IV and encrypted data
            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] encrypted = new byte[byteBuffer.remaining()];
            byteBuffer.get(encrypted);

            // Initialize cipher for decryption
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            // Decrypt the data
            byte[] decrypted = cipher.doFinal(encrypted);
            String combined = new String(decrypted, StandardCharsets.UTF_8);

            // Split back into individual values
            return splitValues(combined);
        } catch (Exception e) {
            throw new HashingException("Failed to decode hash", e);
        }
    }

    private static SecretKey generateKey(String seedPhrase) {
        try {
            // Generate a 256-bit key from the seed phrase using SHA-256
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seedPhrase.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (Exception e) {
            throw new HashingException("Failed to generate key from seed phrase", e);
        }
    }

    private static String joinValues(Object[] values) {
        List<String> encodedValues = new ArrayList<>();
        for (Object value : values) {
            // Encode each value to handle special characters
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
                String.valueOf(value).getBytes(StandardCharsets.UTF_8)
            );
            encodedValues.add(encoded);
        }
        return String.join("|", encodedValues);
    }

    private static String[] splitValues(String combined) {
        String[] encodedValues = combined.split("\\|");
        String[] result = new String[encodedValues.length];
        for (int i = 0; i < encodedValues.length; i++) {
            // Decode each value
            byte[] decoded = Base64.getUrlDecoder().decode(encodedValues[i]);
            result[i] = new String(decoded, StandardCharsets.UTF_8);
        }
        return result;
    }

}
