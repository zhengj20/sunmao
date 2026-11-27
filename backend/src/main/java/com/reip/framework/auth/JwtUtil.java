package com.reip.framework.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${reip.jwt.secret}")
    private String secret;

    @Value("${reip.jwt.expire-hours}")
    private long expireHours;

    @PostConstruct
    void checkSecret() {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException(
                    "reip.jwt.secret 未配置或长度不足 32 位：请设置环境变量 JWT_SECRET，"
                            + "或在 backend/application-local.yml 中配置（参考 application-local.yml.example）");
        }
    }

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(Long userId, String username, String realName, Long orgId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId);
        claims.put("username", username);
        claims.put("realName", realName);
        claims.put("orgId", orgId);
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireHours * 3600_000L))
                .signWith(key())
                .compact();
    }

    /**
     * @return 解析后的登录用户；无效/过期返回 null
     */
    public LoginUser parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key()).build()
                    .parseSignedClaims(token).getPayload();
            Long uid = claims.get("uid", Long.class);
            if (uid == null) return null;
            Object orgId = claims.get("orgId");
            return new LoginUser(uid, claims.get("username", String.class),
                    claims.get("realName", String.class),
                    orgId == null ? null : Long.valueOf(orgId.toString()));
        } catch (Exception e) {
            return null;
        }
    }
}
