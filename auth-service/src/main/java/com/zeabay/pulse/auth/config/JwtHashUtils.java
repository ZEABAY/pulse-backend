package com.zeabay.pulse.auth.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * Shared JWT hashing utilities for the auth-service.
 *
 * <p>When a JWT has no {@code jti} claim, its token value is SHA-256 hashed and used as a
 * substitute key for the token blacklist. This avoids storing raw tokens in Redis.
 */
public final class JwtHashUtils {

  private JwtHashUtils() {}

  /**
   * Computes a URL-safe, unpadded Base64 SHA-256 hash of the given input.
   *
   * @param input the string to hash (typically a raw JWT token value)
   * @return a URL-safe Base64 string without padding
   */
  public static String sha256(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
