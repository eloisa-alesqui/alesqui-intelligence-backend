package es.alesqui.intelligence.util;

import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * Shared utility for hardened client IP extraction from reactive HTTP requests.
 *
 * Security model: proxy headers (X-Forwarded-For, X-Real-IP) are only trusted
 * when the direct TCP peer is a private or loopback address, indicating the
 * request arrived through a legitimate local reverse proxy (Nginx in Docker
 * Compose, Render's load balancer, etc.). Trusting these headers from public
 * internet connections allows trivial spoofing of the client IP.
 */
public final class IpAddressExtractor {

    private IpAddressExtractor() {
        // utility class
    }

    /**
     * Extracts the real client IP address from the HTTP request.
     *
     * @param request the reactive HTTP request
     * @return client IP address, or {@code "unknown"} if not determinable
     */
    public static String extract(ServerHttpRequest request) {
        String remoteAddress = request.getRemoteAddress() != null
                && request.getRemoteAddress().getAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : null;

        // Only trust proxy headers when the direct peer is private/loopback
        if (remoteAddress != null && isPrivateOrLoopback(remoteAddress)) {
            String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                // X-Forwarded-For: client, proxy1, proxy2 — take the leftmost (real client)
                return xForwardedFor.split(",")[0].trim();
            }
            String xRealIp = request.getHeaders().getFirst("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }
        }

        return remoteAddress != null ? remoteAddress : "unknown";
    }

    /**
     * Returns true if the address belongs to a private, loopback, or link-local range.
     *
     * IPv4 private/loopback ranges:
     *   127.x.x.x  — loopback
     *   10.x.x.x   — RFC 1918 class A private
     *   172.16-31.x.x — RFC 1918 class B private (includes Docker bridge)
     *   192.168.x.x — RFC 1918 class C private
     *
     * IPv6 private/loopback ranges:
     *   ::1        — loopback
     *   fe80:      — link-local (RFC 4291)
     *   fc00:/7    — Unique Local Addresses: fc00:: and fd00:: (RFC 4193)
     */
    private static boolean isPrivateOrLoopback(String ip) {
        // IPv4
        if (ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")) {
            return true;
        }
        if (isInDockerBridgeRange(ip)) {
            return true;
        }
        // IPv6 loopback and private ranges
        String lower = ip.toLowerCase();
        if (lower.equals("::1") || lower.startsWith("fe80:") || lower.startsWith("fc00:")
                || lower.startsWith("fd00:")) {
            return true;
        }
        return false;
    }

    /**
     * Returns true for the 172.16.0.0/12 range (172.16.x.x – 172.31.x.x),
     * which covers Docker's default bridge network.
     */
    private static boolean isInDockerBridgeRange(String ip) {
        if (!ip.startsWith("172.")) return false;
        try {
            int second = Integer.parseInt(ip.split("\\.")[1]);
            return second >= 16 && second <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
