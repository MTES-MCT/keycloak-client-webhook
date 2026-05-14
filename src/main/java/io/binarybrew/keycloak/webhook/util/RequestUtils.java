package io.binarybrew.keycloak.webhook.util;

import io.binarybrew.keycloak.webhook.constant.AppConstants;
import org.keycloak.models.KeycloakContext;

import java.util.List;

public class RequestUtils {

    public static int parseTrustedProxyCount(String value) {
        if (value == null || value.trim().isEmpty()) {
            return AppConstants.DEFAULT_TRUSTED_PROXY_COUNT;
        }
        try {
            int count = Integer.parseInt(value.trim());
            return Math.max(0, count);
        } catch (NumberFormatException e) {
            return AppConstants.DEFAULT_TRUSTED_PROXY_COUNT;
        }
    }

    public static String getClientIp(KeycloakContext ctx, String remoteAddr, int trustedProxyCount) {
        if (trustedProxyCount <= 0) {
            return remoteAddr;
        }
        List<String> forwarded = ctx.getRequestHeaders().getRequestHeader(AppConstants.HEADER_X_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isEmpty()) {
            String[] parts = forwarded.get(0).split(",");
            int idx = parts.length - trustedProxyCount;
            if (idx >= 0) {
                return parts[idx].trim();
            }
        }
        List<String> realIp = ctx.getRequestHeaders().getRequestHeader(AppConstants.HEADER_X_REAL_IP);
        if (realIp != null && !realIp.isEmpty()) {
            return realIp.get(0).trim();
        }
        return remoteAddr;
    }

    public static String[] extractRequestHeaders(KeycloakContext ctx, String remoteAddr, int trustedProxyCount) {
        String clientIp = getClientIp(ctx, remoteAddr, trustedProxyCount);
        String userAgent = ctx.getRequestHeaders().getHeaderString(AppConstants.HEADER_USER_AGENT);
        return new String[]{clientIp, userAgent};
    }

    public static String stripKeycloakIdPrefix(String id) {
        if (id == null) {
            return null;
        }
        if (id.startsWith("f:")) {
            return id.substring(2);
        }
        return id;
    }
}
