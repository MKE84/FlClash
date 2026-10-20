package com.follow.clash.service.models

import com.follow.clash.service.transparent.cleanup
import com.follow.clash.service.transparent.redirectRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransparentProxyRulesTest {
    private fun options(dnsPort: Int = 1053) = TransparentProxyOptions(
        enable = true,
        dnsRedirect = dnsPort > 0,
        redirectPort = 7892,
        dnsPort = dnsPort,
    )

    @Test
    fun `keeps the core and loopback out of the redirect`() {
        val rules = redirectRules(10_784, options())
        assertEquals("iptables -t nat -A FLCLASH_OUT -m owner --uid-owner 10784 -j RETURN", rules[1])
        assertEquals("iptables -t nat -A FLCLASH_OUT -d 127.0.0.0/8 -j RETURN", rules[2])
    }

    @Test
    fun `redirects dns before the tcp catch all`() {
        val rules = redirectRules(10_784, options())
        val udp = rules.indexOfFirst { it.contains("--dport 53") && it.contains("-p udp") }
        val tcp = rules.indexOfFirst { it.contains("--dport 53") && it.contains("-p tcp") }
        val catchAll = rules.indexOfFirst { it.endsWith("-p tcp -j REDIRECT --to-ports 7892") }
        assertTrue(udp in 0 until tcp)
        assertTrue(tcp < catchAll)
    }

    @Test
    fun `leaves dns alone when the redirect for it is off`() {
        val rules = redirectRules(10_784, options(dnsPort = 0))
        assertFalse(rules.any { it.contains("--dport 53") })
        assertEquals("iptables -t nat -A FLCLASH_OUT -p tcp -j REDIRECT --to-ports 7892", rules[rules.size - 2])
    }

    @Test
    fun `attaches the chain to output last`() {
        val rules = redirectRules(10_784, options())
        assertEquals("iptables -t nat -I OUTPUT -j FLCLASH_OUT", rules.last())
        assertEquals("iptables -t nat -N FLCLASH_OUT", rules.first())
    }

    @Test
    fun `takes the chain apart in the order iptables allows`() {
        assertEquals(
            listOf(
                "iptables -t nat -D OUTPUT -j FLCLASH_OUT",
                "iptables -t nat -F FLCLASH_OUT",
                "iptables -t nat -X FLCLASH_OUT",
            ),
            cleanup,
        )
    }
}
