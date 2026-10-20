package com.kimkinam.camerashit

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * 재부팅 때(시스템 업데이트 후에도) 셔터음 설정을 확인한다.
 * - 유지되어 있으면: 펌웨어가 바뀌었을 때만 알림.
 * - 풀렸고 자동 적용이 가능하면: 서비스가 코드 없이 다시 적용.
 * - 풀렸고 자동 적용이 불가능하면: 재설정 안내 + GitHub 주소 알림.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!ShutterSound.wantSilent(context)) return

        val updated = ShutterSound.systemUpdated(context)
        val ready = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED && !ShutterSound.needsRepair(context)

        when {
            // 업데이트로 설정 키 자체가 사라졌다면 앱을 고쳐야 한다.
            !ShutterSound.isSupported(context) -> {
                Notifier.notice(context, context.getString(R.string.err_key_blocked))
                Notifier.github(context)
            }
            ShutterSound.isSilent(context) -> if (updated) {
                ShutterSound.rememberSystemVersion(context)
                Notifier.notice(context, context.getString(R.string.notice_kept_after_update))
            }
            ready -> SetupService.start(context, SetupService.ACTION_APPLY, silent = true, updated = updated)
            else -> {
                Notifier.notice(context, context.getString(R.string.notice_need_resetup))
                Notifier.github(context)
            }
        }
    }
}
