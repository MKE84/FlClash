package com.follow.clash.service.transparent

import android.content.Context
import android.os.Process
import com.follow.clash.common.AccessControlMode
import com.follow.clash.service.models.AccessControlProps

fun redirectSelection(
    context: Context,
    props: AccessControlProps,
): RedirectSelection {
    val coreUid = Process.myUid()
    if (!props.enable) {
        return RedirectSelection(coreUid)
    }
    return when (props.mode) {
        AccessControlMode.ACCEPT_SELECTED -> RedirectSelection(
            coreUid = coreUid,
            whitelist = true,
            only = uidsOf(context, props.acceptList + context.packageName) - coreUid,
        )

        AccessControlMode.REJECT_SELECTED -> RedirectSelection(
            coreUid = coreUid,
            bypass = uidsOf(context, props.rejectList) - coreUid,
        )
    }
}

private fun uidsOf(context: Context, packages: List<String>): List<Int> = packages
    .mapNotNull { name ->
        runCatching { context.packageManager.getPackageUid(name, 0) }.getOrNull()
    }
    .distinct()
