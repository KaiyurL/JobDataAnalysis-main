package com.jobdata.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类：负责生成、解析与校验 Token。
 */
@Component
public class JwtUtil {
    private final long expirationMs;
    private final Key secretKey;

    /**
     * 创建 JWT 工具实例。
     *
     * @param secret 签名密钥（字符串形式）
     * @param expirationMs 过期时间（毫秒）
     */
    public JwtUtil(
            @Value("${security.jwt.secret:JobDataSecretKeyForJwtToken1234567890123456}") String secret,
            @Value("${security.jwt.expiration-ms:604800000}") long expirationMs
    ) {
        this.expirationMs = expirationMs;
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 JWT Token。
     *
     * @param username 用户名
     * @param userId 用户 ID
     * @param role 角色
     * @return JWT 字符串
     */
    public String generateToken(String username, Long userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 JWT Token 并返回 Claims。
     *
     * @param token JWT 字符串
     * @return Claims
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 获取 Token 中的用户名（subject）。
     *
     * @param token JWT 字符串
     * @return 用户名
     */
    public String getUsername(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * 获取 Token 中的用户 ID。
     *
     * @param token JWT 字符串
     * @return 用户 ID
     */
    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).get("userId").toString());
    }

    /**
     * 获取 Token 中的角色。
     *
     * @param token JWT 字符串
     * @return 角色（可能为 null）
     */
    public String getRole(String token) {
        Object v = parseToken(token).get("role");
        return v == null ? null : String.valueOf(v);
    }

    /**
     * 校验 Token 是否有效（签名正确且未过期）。
     *
     * @param token JWT 字符串
     * @return 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
