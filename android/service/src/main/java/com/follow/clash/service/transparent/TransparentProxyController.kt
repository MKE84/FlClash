package com.follow.clash.service.transparent

import com.follow.clash.common.GlobalState
import com.follow.clash.service.models.TransparentProxyOptions
import java.io.File

private const val LOG_TAG = "TransparentProxy"

private const val MARKER_FILE = "transparent-proxy.rules"

// A run killed without a stop leaves its chain in the kernel, and every app it
// covered then talks to a closed port. The marker records that the rules may
// exist, so the next launch clears them before they can matter.
object TransparentProxyController {
    @Volatile
    private var installed: TransparentProxyOptions? = null

    val active: TransparentProxyOptions?
        get() = installed

    @Synchronized
    fun apply(options: TransparentProxyOptions, selection: RedirectSelection) {
        if (!options.usable) {
            return
        }
        if (!Su.isAvailable()) {
            throw Su.Failure("root is not available")
        }
        val binary = Iptables.resolve(Iptables.v4Candidates, "nat")
            ?: throw Su.Failure("iptables is not available")
        val binary6 = if (options.ipv6Block) {
            Iptables.resolve(Iptables.v6Candidates, "filter")
        } else {
            null
        }
        GlobalState.log(
            "$LOG_TAG: redirecting tcp to ${options.redirectPort}" +
                (if (options.usableDns) ", dns to ${options.dnsPort}" else "") +
                (if (selection.whitelist) ", ${selection.only.size} apps" else "") +
                (if (binary6 == null) "" else ", ipv6 refused"),
        )
        Su.silent(cleanup(binary))
        Su.silent(cleanupIpv6(binary6 ?: DEFAULT_IP6TABLES))
        Su.script(redirectRules(selection, options, binary))
        if (binary6 != null) {
            runCatching {
                Su.script(ipv6Rules(selection, binary6))
            }.onFailure { error ->
                GlobalState.log("$LOG_TAG: ipv6 rules failed: ${error.message}")
            }
        }
        installed = options
        writeMarker(binary6 != null)
    }

    @Synchronized
    fun remove() {
        val hadInstalled = installed != null
        val refusedIpv6 = readMarker()
        installed = null
        writeMarker(false)
        if (hadInstalled) {
            GlobalState.log("$LOG_TAG: removing the redirect")
        }
        val binary = Iptables.resolve(Iptables.v4Candidates, "nat") ?: DEFAULT_IPTABLES
        Su.silent(cleanup(binary))
        if (refusedIpv6) {
            val binary6 = Iptables.resolve(Iptables.v6Candidates, "filter") ?: DEFAULT_IP6TABLES
            Su.silent(cleanupIpv6(binary6))
        }
    }

    fun clearStale() {
        if (!marker().exists()) {
            return
        }
        GlobalState.log("$LOG_TAG: clearing the redirect an earlier run left behind")
        runCatching { remove() }
    }

    private fun marker(): File = File(GlobalState.application.filesDir, MARKER_FILE)

    private fun readMarker(): Boolean =
        runCatching { marker().readText().trim() == "1" }.getOrDefault(false)

    private fun writeMarker(refusedIpv6: Boolean) {
        runCatching {
            val marker = marker()
            if (!refusedIpv6) {
                marker.delete()
                return@runCatching
            }
            marker.writeText("1")
        }
    }
}

internal object Iptables {
    val v4Candidates = listOf(DEFAULT_IPTABLES, "/system/bin/$DEFAULT_IPTABLES", "iptables-nft")

    val v6Candidates = listOf(DEFAULT_IP6TABLES, "/system/bin/$DEFAULT_IP6TABLES", "ip6tables-nft")

    fun resolve(candidates: List<String>, table: String): String? = candidates.firstOrNull { binary ->
        runCatching { Su.exec("$binary -t $table -L OUTPUT -n > /dev/null 2>&1") }.isSuccess
    }
}
