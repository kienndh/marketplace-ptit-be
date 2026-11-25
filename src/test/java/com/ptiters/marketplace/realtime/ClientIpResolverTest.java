package com.ptiters.marketplace.realtime;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class ClientIpResolverTest {

    private final ClientIpResolver resolver = new ClientIpResolver();

    @Test
    @DisplayName("Tin cậy CF-Connecting-IP công khai khi X-Real-IP thuộc dải Cloudflare IPv4")
    void trustsCfConnectingIpWhenXRealIpIsCloudflareIpv4() {
        HttpServletRequest req = Mockito.mock(HttpServletRequest.class);
        when(req.getHeader("X-Real-IP")).thenReturn("104.16.1.5");
        when(req.getHeader("CF-Connecting-IP")).thenReturn("203.0.113.45");
        when(req.getRemoteAddr()).thenReturn("10.0.0.2");

        assertEquals("203.0.113.45", resolver.resolveClientIp(req));
    }

    @Test
    @DisplayName("Tin cậy CF-Connecting-IP khi X-Real-IP thuộc dải Cloudflare IPv6")
    void trustsCfConnectingIpWhenXRealIpIsCloudflareIpv6() {
        assertEquals("198.51.100.77",
                resolver.resolveClientIp("2400:cb00:1::1", "198.51.100.77:44321", "127.0.0.1"));
    }

    @Test
    @DisplayName("Loại bỏ CF-Connecting-IP giả mạo khi X-Real-IP không phải Cloudflare")
    void discardsSpoofedCfConnectingIpWhenPeerIsNotCloudflare() {
        assertEquals("198.51.100.10",
                resolver.resolveClientIp("198.51.100.10", "1.2.3.4", "127.0.0.1"));
    }

    @Test
    @DisplayName("Bỏ qua CF-Connecting-IP nếu chứa IP nội bộ và fallback về Cloudflare peer IP")
    void ignoresInternalProxyCfConnectingIp() {
        assertEquals("172.64.0.1",
                resolver.resolveClientIp("172.64.0.1", "10.0.0.5", "127.0.0.1"));
    }

    @Test
    @DisplayName("Không tin cậy CF-Connecting-IP khi thiếu hoặc sai định dạng X-Real-IP")
    void ignoresCfConnectingIpWhenXRealIpMissingOrInvalid() {
        assertEquals("192.0.2.99",
                resolver.resolveClientIp("not-an-ip", "8.8.8.8", "192.0.2.99"));
        assertEquals("unknown",
                resolver.resolveClientIp(null, "8.8.8.8", null));
    }

    @Test
    @DisplayName("parseIpSafe tách port IPv4/IPv6 và từ chối hostname không phải IP")
    void parseIpSafeHandlesPortsAndRejectsHostnames() {
        assertNotNull(ClientIpResolver.parseIpSafe("203.0.113.5:8080"));
        assertNotNull(ClientIpResolver.parseIpSafe("[2400:cb00::1]:443"));
        assertNotNull(ClientIpResolver.parseIpSafe("::1"));
        assertNull(ClientIpResolver.parseIpSafe("evil.example.com"));
        assertTrue(ClientIpResolver.isCloudflareIp(ClientIpResolver.parseIpSafe("162.158.10.20")));
        assertFalse(ClientIpResolver.isCloudflareIp(ClientIpResolver.parseIpSafe("8.8.8.8")));
    }

    @Test
    @DisplayName("maskIp ẩn phần cuối IPv4 và IPv6 giống Python")
    void maskIpMasksIpv4AndIpv6() {
        assertEquals("203.0.113.***", ClientIpResolver.maskIp("203.0.113.195"));
        assertEquals("2001:0db8:85a3:****", ClientIpResolver.maskIp("2001:db8:85a3::8a2e:370:7334"));
    }
}
