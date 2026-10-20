package com.follow.clash.service.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun options(
    enable: Boolean = true,
    dnsRedirect: Boolean = true,
    redirectPort: Int = 7892,
    dnsPort: Int = 1053,
) = TransparentProxyOptions(enable, dnsRedirect, redirectPort, dnsPort)

class TransparentProxyOptionsTest {
    @Test
    fun `a redirect without a port is not usable`() {
        assertFalse(options(redirectPort = 0).usable)
        assertFalse(options(redirectPort = -1).usable)
        assertFalse(options(redirectPort = 70000).usable)
    }

    @Test
    fun `turning it off drops the ports`() {
        val started = options(enable = false).started

        assertFalse(started.enable)
        assertEquals(0, started.redirectPort)
        assertEquals(0, started.dnsPort)
    }

    @Test
    fun `dns redirect survives only with a port`() {
        assertTrue(options().started.dnsRedirect)
        assertTrue(options(dnsRedirect = false).started.dnsRedirect.not())
        assertFalse(options(dnsPort = 0).started.dnsRedirect)
    }

    @Test
    fun `what a run installs is what a restart compares`() {
        assertEquals(options().started, options().started)
        assertTrue(options(redirectPort = 7893).started != options().started)
        assertTrue(options(dnsPort = 1054).started != options().started)
        // A redirect that cannot be written and one that is off are one run.
        assertEquals(options(enable = false).started, options(redirectPort = 0).started)
    }
}
