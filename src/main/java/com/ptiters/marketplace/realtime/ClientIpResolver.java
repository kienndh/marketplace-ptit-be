package com.ptiters.marketplace.realtime;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Xác định địa chỉ IP thực của client theo mô hình Cloudflare Edge -> Reverse Proxy -> Backend.
 * Hiện thực tương đương {@code app/core/client_ip.py}:
 * <ul>
 *   <li>Không tin cậy {@code CF-Connecting-IP} hay {@code X-Forwarded-For} từ nguồn không xác thực.</li>
 *   <li>Chỉ chấp nhận {@code CF-Connecting-IP} (và không thuộc dải nội bộ) khi {@code X-Real-IP} thuộc dải IP chính thức của Cloudflare.</li>
 *   <li>Cung cấp {@link #maskIp(String)} để che bớt IP khi ghi log bảo mật.</li>
 * </ul>
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    private static final Pattern IPV4_SYNTAX = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
    private static final Pattern IPV6_ALLOWED_CHARS = Pattern.compile("^[0-9a-fA-F:.]+$");

    private static final List<CidrBlock> CLOUDFLARE_NETWORKS = List.of(
            // Official Cloudflare IPv4 CIDRs
            CidrBlock.of("173.245.48.0/20"),
            CidrBlock.of("103.21.244.0/22"),
            CidrBlock.of("103.22.200.0/22"),
            CidrBlock.of("103.31.4.0/22"),
            CidrBlock.of("141.101.64.0/18"),
            CidrBlock.of("108.162.192.0/18"),
            CidrBlock.of("190.93.240.0/20"),
            CidrBlock.of("188.114.96.0/20"),
            CidrBlock.of("197.234.240.0/22"),
            CidrBlock.of("198.41.128.0/17"),
            CidrBlock.of("162.158.0.0/15"),
            CidrBlock.of("104.16.0.0/13"),
            CidrBlock.of("104.24.0.0/14"),
            CidrBlock.of("172.64.0.0/13"),
            CidrBlock.of("131.0.72.0/22"),
            // Official Cloudflare IPv6 CIDRs
            CidrBlock.of("2400:cb00::/32"),
            CidrBlock.of("2606:4700::/32"),
            CidrBlock.of("2803:f800::/32"),
            CidrBlock.of("2405:b500::/32"),
            CidrBlock.of("2405:8100::/32"),
            CidrBlock.of("2a06:98c0::/29"),
            CidrBlock.of("2c0f:f248::/32")
    );

    private static final List<CidrBlock> INTERNAL_PROXY_NETWORKS = List.of(
            CidrBlock.of("10.0.0.0/8"),
            CidrBlock.of("172.16.0.0/12"),
            CidrBlock.of("192.168.0.0/16"),
            CidrBlock.of("127.0.0.0/8"),
            CidrBlock.of("100.64.0.0/10"),
            CidrBlock.of("169.254.0.0/16"),
            CidrBlock.of("fc00::/7"),
            CidrBlock.of("::1/128")
    );

    public String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        return resolveClientIp(
                request.getHeader("X-Real-IP"),
                request.getHeader("CF-Connecting-IP"),
                request.getRemoteAddr()
        );
    }

    public String resolveClientIp(WebSocketSession session) {
        if (session == null) {
            return "unknown";
        }
        HttpHeaders headers = session.getHandshakeHeaders();
        String xRealIp = headers.getFirst("X-Real-IP");
        String cfConnectingIp = headers.getFirst("CF-Connecting-IP");
        InetSocketAddress remote = session.getRemoteAddress();
        String remoteAddr = (remote != null && remote.getAddress() != null)
                ? remote.getAddress().getHostAddress()
                : null;
        return resolveClientIp(xRealIp, cfConnectingIp, remoteAddr);
    }

    public String resolveClientIp(String xRealIpRaw, String cfConnectingIpRaw, String remoteAddr) {
        InetAddress realIp = parseIpSafe(xRealIpRaw);
        if (realIp != null) {
            if (isCloudflareIp(realIp)) {
                InetAddress cfIp = parseIpSafe(cfConnectingIpRaw);
                if (cfIp != null && !isInternalProxyIp(cfIp)) {
                    return cfIp.getHostAddress();
                }
                return realIp.getHostAddress();
            }
            if (cfConnectingIpRaw != null && !cfConnectingIpRaw.isBlank()) {
                log.debug("Discarded untrusted CF-Connecting-IP from non-Cloudflare peer {}", maskIp(realIp.getHostAddress()));
            }
            return realIp.getHostAddress();
        }

        if (remoteAddr != null && !remoteAddr.isBlank()) {
            String trimmed = remoteAddr.trim();
            InetAddress parsedClient = parseIpSafe(trimmed);
            return parsedClient != null ? parsedClient.getHostAddress() : trimmed;
        }
        return "unknown";
    }

    public static InetAddress parseIpSafe(String ipStr) {
        if (ipStr == null) {
            return null;
        }
        String raw = ipStr.trim();
        if (raw.isEmpty()) {
            return null;
        }

        if (raw.startsWith("[") && raw.contains("]")) {
            raw = raw.substring(1, raw.indexOf(']'));
        } else {
            int firstColon = raw.indexOf(':');
            if (firstColon > 0 && raw.indexOf(':', firstColon + 1) < 0) {
                // Single colon in IPv4:port
                raw = raw.substring(0, firstColon);
            }
        }

        if (raw.isEmpty()) {
            return null;
        }

        // Guard against DNS lookups by validating literal IPv4 / IPv6 syntax before InetAddress.getByName
        boolean looksIpv4 = IPV4_SYNTAX.matcher(raw).matches();
        boolean looksIpv6 = raw.contains(":") && IPV6_ALLOWED_CHARS.matcher(raw).matches();
        if (!looksIpv4 && !looksIpv6) {
            return null;
        }

        try {
            return InetAddress.getByName(raw);
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public static boolean isCloudflareIp(InetAddress ip) {
        return ip != null && CLOUDFLARE_NETWORKS.stream().anyMatch(cidr -> cidr.contains(ip));
    }

    public static boolean isInternalProxyIp(InetAddress ip) {
        return ip != null && INTERNAL_PROXY_NETWORKS.stream().anyMatch(cidr -> cidr.contains(ip));
    }

    public static String maskIp(String ipStr) {
        InetAddress ip = parseIpSafe(ipStr);
        if (ip == null) {
            return ipStr == null ? "unknown" : ipStr;
        }
        if (ip instanceof Inet4Address) {
            String[] octets = ip.getHostAddress().split("\\.");
            if (octets.length == 4) {
                return octets[0] + "." + octets[1] + "." + octets[2] + ".***";
            }
        } else if (ip instanceof Inet6Address) {
            byte[] bytes = ip.getAddress();
            if (bytes.length == 16) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 3; i++) {
                    if (i > 0) {
                        sb.append(':');
                    }
                    int hextet = ((bytes[i * 2] & 0xff) << 8) | (bytes[i * 2 + 1] & 0xff);
                    sb.append(String.format("%04x", hextet));
                }
                return sb.append(":****").toString();
            }
        }
        return "***";
    }

    private record CidrBlock(byte[] networkBytes, int prefixLength) {

        static CidrBlock of(String cidr) {
            String[] parts = cidr.split("/");
            InetAddress addr = parseIpSafe(parts[0]);
            if (addr == null) {
                throw new IllegalArgumentException("Invalid CIDR network: " + cidr);
            }
            return new CidrBlock(addr.getAddress(), Integer.parseInt(parts[1]));
        }

        boolean contains(InetAddress candidate) {
            byte[] target = candidate.getAddress();
            if (target.length != networkBytes.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            int remainingBits = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (target[i] != networkBytes[i]) {
                    return false;
                }
            }
            if (remainingBits > 0) {
                int mask = (0xff << (8 - remainingBits)) & 0xff;
                return (target[fullBytes] & mask) == (networkBytes[fullBytes] & mask);
            }
            return true;
        }
    }
}
