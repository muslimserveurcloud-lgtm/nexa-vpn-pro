package com.nexavpn.pro

import android.content.Context
import android.content.Intent

fun startVpn(ctx: Context) {
    ctx.startService(Intent(ctx, NexaVpnService::class.java).setAction(NexaVpnService.ACTION_START))
}

fun stopVpn(ctx: Context) {
    ctx.startService(Intent(ctx, NexaVpnService::class.java).setAction(NexaVpnService.ACTION_STOP))
}
