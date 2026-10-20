package com.follow.clash.service.transparent

import android.os.Process
import com.follow.clash.common.GlobalState
import com.follow.clash.service.models.TransparentProxyOptions

private const val LOG_TAG = "TransparentProxy"

private const val CHAIN = "FLCLASH_OUT"

// The uid and loopback returns keep the core's own sockets out; apply() also
// clears the previous kernel rules before writing the new ports.
object TransparentProxyController {
    @Volatile
    private var installed: TransparentProxyOptions? = null

    val active: TransparentProxyOptions?
        get() = installed

    @Synchronized
    fun apply(options: TransparentProxyOptions) {
        if (!options.usable) {
            return
        }
        if (!Su.isAvailable()) {
            throw Su.Failure("root is not available")
        }
        GlobalState.log(
            "$LOG_TAG: redirecting tcp to ${options.redirectPort}" +
                if (options.usableDns) ", dns to ${options.dnsPort}" else "",
        )
        Su.execAll(cleanup, ignoreFailure = true)
        Su.execAll(redirectRules(Process.myUid(), options))
        installed = options
    }

    @Synchronized
    fun remove() {
        val hadInstalled = installed != null
        installed = null
        if (hadInstalled) {
            GlobalState.log("$LOG_TAG: removing the redirect")
        }
        Su.execAll(cleanup, ignoreFailure = true)
    }
}

internal val cleanup = listOf(
    "iptables -t nat -D OUTPUT -j $CHAIN",
    "iptables -t nat -F $CHAIN",
    "iptables -t nat -X $CHAIN",
)

// The returns come first: the core's own sockets and loopback traffic must not
// reach the redirect that follows them.
internal fun redirectRules(uid: Int, options: TransparentProxyOptions): List<String> = buildList {
    add("iptables -t nat -N $CHAIN")
    add("iptables -t nat -A $CHAIN -m owner --uid-owner $uid -j RETURN")
    add("iptables -t nat -A $CHAIN -d 127.0.0.0/8 -j RETURN")
    if (options.usableDns) {
        add("iptables -t nat -A $CHAIN -p udp --dport 53 -j REDIRECT --to-ports ${options.dnsPort}")
        add("iptables -t nat -A $CHAIN -p tcp --dport 53 -j REDIRECT --to-ports ${options.dnsPort}")
    }
    add("iptables -t nat -A $CHAIN -p tcp -j REDIRECT --to-ports ${options.redirectPort}")
    add("iptables -t nat -I OUTPUT -j $CHAIN")
}
