package com.follow.clash.service.models

// Only a port the core binds from the profile may reach a rule.
data class TransparentProxyOptions(
    val enable: Boolean,
    val dnsRedirect: Boolean,
    val redirectPort: Int,
    val dnsPort: Int,
    val ipv6Block: Boolean = false,
) {
    val usable: Boolean
        get() = enable && redirectPort in 1..65535

    val usableDns: Boolean
        get() = usable && dnsRedirect && dnsPort in 1..65535

    val started: TransparentProxyOptions
        get() = if (usable) {
            copy(dnsRedirect = usableDns)
        } else {
            TransparentProxyOptions(
                enable = false,
                dnsRedirect = false,
                redirectPort = 0,
                dnsPort = 0,
                ipv6Block = false,
            )
        }
}
