package com.kimkinam.camerashit

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** 재부팅/업데이트로 설정이 풀렸으면 사용자 조작 없이 다시 적용한다(설정 1회 완료 후에만). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val ready = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        if (ready && ShutterSound.wantSilent(context) && !ShutterSound.isSilent(context)) {
            SetupService.start(context, SetupService.ACTION_APPLY, silent = true)
        }
    }
}
