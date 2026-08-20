package com.finco.lab.oauthclient.web;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pulls a token apart so you can see what is inside it — without pasting a live token into
 * jwt.io, which is a habit that gets people fired in fintech.
 *
 * <p>PingFederate can issue two very different things as an access token:</p>
 * <ul>
 *   <li>a <b>JWT</b> — self-contained, readable here, verified by the API using the JWKS; or</li>
 *   <li>a <b>reference token</b> — an opaque random string that means nothing on its own.
 *       The API must call PingFederate's introspection endpoint to find out who it belongs to.</li>
 * </ul>
 *
 * <p>Which one you get is decided by the <b>Access Token Manager</b> attached to your client.
 * This class tells you which one you were handed.</p>
 */
final class TokenInspector {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TokenInspector() {
    }

    static Map<String, Object> inspect(String token) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (token == null || token.isBlank()) {
            result.put("present", false);
            return result;
        }
        result.put("present", true);
        result.put("length", token.length());
        result.put("preview", preview(token));

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            result.put("format", "opaque");
            result.put("note", "This is a PingFederate reference token, not a JWT. It carries no "
                    + "claims — call POST /api/introspect to ask PingFederate what it means.");
            return result;
        }

        try {
            Map<String, Object> header = decodeSegment(parts[0]);
            Map<String, Object> payload = decodeSegment(parts[1]);
            result.put("format", "jwt");
            result.put("header", header);
            result.put("payload", payload);
            result.put("signaturePresent", !parts[2].isBlank());
            Object exp = payload.get("exp");
            if (exp instanceof Number expiry) {
                Instant expiresAt = Instant.ofEpochSecond(expiry.longValue());
                result.put("expiresAt", expiresAt.toString());
                result.put("expired", expiresAt.isBefore(Instant.now()));
                result.put("secondsRemaining", Math.max(0, expiresAt.getEpochSecond() - Instant.now().getEpochSecond()));
            }
        } catch (Exception ex) {
            result.put("format", "unparseable");
            result.put("error", ex.getMessage());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> decodeSegment(String segment) throws Exception {
        byte[] decoded = Base64.getUrlDecoder().decode(segment);
        return MAPPER.readValue(new String(decoded, StandardCharsets.UTF_8), Map.class);
    }

    /**
     * Never render a whole live token in a browser tab you might screenshot. Ends only —
     * enough to match it against a log line, useless to an attacker who sees your screen.
     */
    static String preview(String token) {
        if (token.length() <= 24) {
            return "****";
        }
        return token.substring(0, 12) + "…" + token.substring(token.length() - 8);
    }
}
