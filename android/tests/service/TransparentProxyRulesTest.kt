package com.follow.clash.service.models

import com.follow.clash.service.transparent.RedirectSelection
import com.follow.clash.service.transparent.cleanup
import com.follow.clash.service.transparent.cleanupIpv6
import com.follow.clash.service.transparent.ipv6Rules
import com.follow.clash.service.transparent.privateRanges
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

    private fun selection(
        whitelist: Boolean = false,
        only: List<Int> = emptyList(),
        bypass: List<Int> = emptyList(),
    ) = RedirectSelection(
        coreUid = 10_784,
        whitelist = whitelist,
        only = only,
        bypass = bypass,
    )

    @Test
    fun `keeps the core and loopback out of the redirect`() {
        val rules = redirectRules(selection(), options())
        assertEquals("iptables -t nat -A FLCLASH_OUT -m owner --uid-owner 10784 -j RETURN", rules[1])
        assertEquals("iptables -t nat -A FLCLASH_OUT -d 127.0.0.0/8 -j RETURN", rules[2])
    }

    @Test
    fun `keeps the local network out of the redirect`() {
        val rules = redirectRules(selection(), options())
        for (range in privateRanges) {
            assertTrue(range, rules.contains("iptables -t nat -A FLCLASH_OUT -d $range -j RETURN"))
        }
    }

    @Test
    fun `redirects dns before the tcp catch all`() {
        val rules = redirectRules(selection(), options())
        val udp = rules.indexOfFirst { it.contains("--dport 53") && it.contains("-p udp") }
        val tcp = rules.indexOfFirst { it.contains("--dport 53") && it.contains("-p tcp") }
        val catchAll = rules.indexOfFirst { it.endsWith("-p tcp -j REDIRECT --to-ports 7892") }
        assertTrue(udp in 0 until tcp)
        assertTrue(tcp < catchAll)
    }

    @Test
    fun `leaves dns alone when the redirect for it is off`() {
        val rules = redirectRules(selection(), options(dnsPort = 0))
        assertFalse(rules.any { it.contains("--dport 53") })
        assertEquals("iptables -t nat -A FLCLASH_OUT -p tcp -j REDIRECT --to-ports 7892", rules[rules.size - 2])
    }

    @Test
    fun `attaches the chain to output last`() {
        val rules = redirectRules(selection(), options())
        assertEquals("iptables -t nat -I OUTPUT -j FLCLASH_OUT", rules.last())
        assertEquals("iptables -t nat -N FLCLASH_OUT", rules.first())
    }

    @Test
    fun `keeps a bypassed app out of a redirect it would otherwise get`() {
        val rules = redirectRules(selection(bypass = listOf(10_001, 10_002)), options())
        val first = rules.indexOfFirst { it.contains("--uid-owner 10001 -j RETURN") }
        val catchAll = rules.indexOfFirst { it.endsWith("-p tcp -j REDIRECT --to-ports 7892") }
        assertTrue(rules.contains("iptables -t nat -A FLCLASH_OUT -m owner --uid-owner 10002 -j RETURN"))
        assertTrue(first in 0 until catchAll)
    }

    @Test
    fun `redirects only the chosen apps in a whitelist`() {
        val rules = redirectRules(selection(whitelist = true, only = listOf(10_001)), options())
        assertTrue(
            rules.contains(
                "iptables -t nat -A FLCLASH_OUT -m owner --uid-owner 10001 -p tcp -j REDIRECT --to-ports 7892",
            ),
        )
        assertTrue(
            rules.contains(
                "iptables -t nat -A FLCLASH_OUT -m owner --uid-owner 10001 -p udp --dport 53 -j REDIRECT --to-ports 1053",
            ),
        )
        assertFalse(
            rules.contains("iptables -t nat -A FLCLASH_OUT -p tcp -j REDIRECT --to-ports 7892"),
        )
        assertEquals("iptables -t nat -A FLCLASH_OUT -j RETURN", rules[rules.size - 2])
    }

    @Test
    fun `redirects nobody when the whitelist is empty`() {
        val rules = redirectRules(selection(whitelist = true), options())
        assertFalse(rules.any { it.contains("REDIRECT") })
        assertEquals("iptables -t nat -A FLCLASH_OUT -j RETURN", rules[rules.size - 2])
        val ipv6 = ipv6Rules(selection(whitelist = true))
        assertFalse(ipv6.any { it.contains("REJECT") })
    }

    @Test
    fun `refuses ipv6 for the apps a redirect cannot carry`() {
        val rules = ipv6Rules(selection(bypass = listOf(10_001)))
        assertEquals("ip6tables -t filter -A FLCLASH_OUT6 -m owner --uid-owner 10001 -j RETURN", rules[3])
        assertEquals("ip6tables -t filter -A FLCLASH_OUT6 -j REJECT", rules[4])
        assertEquals("ip6tables -t filter -I OUTPUT -j FLCLASH_OUT6", rules.last())
    }

    @Test
    fun `leaves the ipv6 of a bypassed app alone in a whitelist`() {
        val rules = ipv6Rules(selection(whitelist = true, only = listOf(10_001)))
        assertTrue(
            rules.contains(
                "ip6tables -t filter -A FLCLASH_OUT6 -m owner --uid-owner 10001 -j REJECT",
            ),
        )
        assertEquals("ip6tables -t filter -A FLCLASH_OUT6 -j RETURN", rules[rules.size - 2])
    }

    @Test
    fun `takes the chain apart in the order iptables allows`() {
        assertEquals(
            listOf(
                "iptables -t nat -D OUTPUT -j FLCLASH_OUT",
                "iptables -t nat -F FLCLASH_OUT",
                "iptables -t nat -X FLCLASH_OUT",
            ),
            cleanup(),
        )
        assertEquals(
            listOf(
                "ip6tables -t filter -D OUTPUT -j FLCLASH_OUT6",
                "ip6tables -t filter -F FLCLASH_OUT6",
                "ip6tables -t filter -X FLCLASH_OUT6",
            ),
            cleanupIpv6(),
        )
    }
}
