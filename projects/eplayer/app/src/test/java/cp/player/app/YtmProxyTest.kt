package cp.player.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtmProxyTest {
    @Test fun hostAndPortBecomeASocks5Endpoint() {
        val config = YtmProxy.parse("127.0.0.1:10808")!!
        assertEquals("127.0.0.1", config.host)
        assertEquals(10808, config.port)
        assertEquals("127.0.0.1:10808", config.address())
        assertEquals(java.net.Proxy.Type.SOCKS, config.toProxy().type())
    }

    @Test fun theSocks5SchemePrefixIsAcceptedAndStripped() {
        assertEquals("127.0.0.1:10808", YtmProxy.parse("socks5://127.0.0.1:10808")!!.address())
        assertEquals("127.0.0.1:10808", YtmProxy.parse("SOCKS5://127.0.0.1:10808")!!.address())
        assertEquals("127.0.0.1:10808", YtmProxy.parse("socks://127.0.0.1:10808")!!.address())
        assertEquals("proxy.lan", YtmProxy.parse("  proxy.lan:1080  ")!!.host)
    }

    @Test fun httpProxyIsSupportedWithoutPretendingHttpsProxySupport() {
        val config = YtmProxy.parse("http://127.0.0.1:7890")!!
        assertEquals(java.net.Proxy.Type.HTTP, config.kind)
        assertEquals(java.net.Proxy.Type.HTTP, config.toProxy().type())
        assertNull(YtmProxy.parse("https://127.0.0.1:7890"))
    }

    @Test fun tunnelProtocolLinksAreRejectedAndFlagged() {
        for (link in listOf("ss://YWVzLTI1Ni1nY206cGFzcw@host:443#name", "vmess://eyJhZGQiOiJob3N0In0=",
            "vless://uuid@host:443?type=ws&security=tls", "trojan://pw@host:443", "hysteria2://host:443")) {
            assertNull("should reject: $link", YtmProxy.parse(link))
            assertTrue("should be flagged as tunnel: $link", YtmProxy.isTunnelLink(link))
        }
        assertFalse(YtmProxy.isTunnelLink("socks5://127.0.0.1:10808"))
        assertFalse(YtmProxy.isTunnelLink("127.0.0.1:10808"))
    }

    @Test fun blankOrMalformedInputDoesNotBecomeAProxy() {
        for (bad in listOf(null, "", "   ", "10808", "host:", ":10808", "host:abc", "host:0", "host:70000")) {
            assertNull("should reject: $bad", YtmProxy.parse(bad))
        }
    }

    @Test fun ipv6LiteralHostIsUnwrapped() {
        assertEquals("::1", YtmProxy.parse("[::1]:10808")!!.host)
        assertEquals(10808, YtmProxy.parse("[::1]:10808")!!.port)
    }
    @Test fun malformedHostsAndPathsAreRejected() {
        for (raw in listOf("bad host:1080", "user@host:1080", "host/path:1080", "::1:1080")) assertNull(YtmProxy.parse(raw))
        assertEquals("[::1]:1080", YtmProxy.parse("[::1]:1080")!!.address())
    }

}
