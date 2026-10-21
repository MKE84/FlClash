package com.follow.clash.service.transparent

import com.follow.clash.service.models.TransparentProxyOptions

internal const val CHAIN = "FLCLASH_OUT"

internal const val CHAIN6 = "FLCLASH_OUT6"

internal const val DEFAULT_IPTABLES = "iptables"

internal const val DEFAULT_IP6TABLES = "ip6tables"

data class RedirectSelection(
    val coreUid: Int,
    val whitelist: Boolean = false,
    val only: List<Int> = emptyList(),
    val bypass: List<Int> = emptyList(),
)

internal val privateRanges = listOf(
    "10.0.0.0/8",
    "172.16.0.0/12",
    "192.168.0.0/16",
    "169.254.0.0/16",
)

// The returns come first: the core's own sockets, loopback and the local network
// must not reach the redirect that follows them.
internal fun redirectRules(
    selection: RedirectSelection,
    options: TransparentProxyOptions,
    binary: String = DEFAULT_IPTABLES,
): List<String> = buildList {
    add("$binary -t nat -N $CHAIN")
    add("$binary -t nat -A $CHAIN -m owner --uid-owner ${selection.coreUid} -j RETURN")
    add("$binary -t nat -A $CHAIN -d 127.0.0.0/8 -j RETURN")
    for (range in privateRanges) {
        add("$binary -t nat -A $CHAIN -d $range -j RETURN")
    }
    for (uid in selection.bypass) {
        add("$binary -t nat -A $CHAIN -m owner --uid-owner $uid -j RETURN")
    }
    if (selection.whitelist) {
        for (uid in selection.only) {
            if (options.usableDns) {
                add("$binary -t nat -A $CHAIN -m owner --uid-owner $uid -p udp --dport 53 -j REDIRECT --to-ports ${options.dnsPort}")
                add("$binary -t nat -A $CHAIN -m owner --uid-owner $uid -p tcp --dport 53 -j REDIRECT --to-ports ${options.dnsPort}")
            }
            add("$binary -t nat -A $CHAIN -m owner --uid-owner $uid -p tcp -j REDIRECT --to-ports ${options.redirectPort}")
        }
        add("$binary -t nat -A $CHAIN -j RETURN")
    } else {
        if (options.usableDns) {
            add("$binary -t nat -A $CHAIN -p udp --dport 53 -j REDIRECT --to-ports ${options.dnsPort}")
            add("$binary -t nat -A $CHAIN -p tcp --dport 53 -j REDIRECT --to-ports ${options.dnsPort}")
        }
        add("$binary -t nat -A $CHAIN -p tcp -j REDIRECT --to-ports ${options.redirectPort}")
    }
    add("$binary -t nat -I OUTPUT -j $CHAIN")
}

internal fun ipv6Rules(
    selection: RedirectSelection,
    binary: String = DEFAULT_IP6TABLES,
): List<String> = buildList {
    add("$binary -t filter -N $CHAIN6")
    add("$binary -t filter -A $CHAIN6 -m owner --uid-owner ${selection.coreUid} -j RETURN")
    add("$binary -t filter -A $CHAIN6 -d ::1/128 -j RETURN")
    for (uid in selection.bypass) {
        add("$binary -t filter -A $CHAIN6 -m owner --uid-owner $uid -j RETURN")
    }
    if (selection.whitelist) {
        for (uid in selection.only) {
            add("$binary -t filter -A $CHAIN6 -m owner --uid-owner $uid -j REJECT")
        }
        add("$binary -t filter -A $CHAIN6 -j RETURN")
    } else {
        add("$binary -t filter -A $CHAIN6 -j REJECT")
    }
    add("$binary -t filter -I OUTPUT -j $CHAIN6")
}

internal fun cleanup(binary: String = DEFAULT_IPTABLES): List<String> = listOf(
    "$binary -t nat -D OUTPUT -j $CHAIN",
    "$binary -t nat -F $CHAIN",
    "$binary -t nat -X $CHAIN",
)

internal fun cleanupIpv6(binary: String = DEFAULT_IP6TABLES): List<String> = listOf(
    "$binary -t filter -D OUTPUT -j $CHAIN6",
    "$binary -t filter -F $CHAIN6",
    "$binary -t filter -X $CHAIN6",
)
