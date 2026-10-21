package com.kimkinam.camerashit

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * 재부팅 때(시스템 업데이트 후에도) 셔터음 설정을 확인하고 알림을 담당한다.
 *
 * 도우미 앱이 있으면 도우미가 부팅 때 Wi-Fi/adb 없이 스스로 값을 되돌린다. 본 앱은 도우미에게 한 번 더 확인시켜서
 * (같은 일을 두 번 해도 안전하다) 결과에 맞는 알림만 보낸다. 도우미가 없거나 쓰기에 실패하면 무선 디버깅 방식으로 대체한다.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!ShutterSound.wantSilent(context)) return
        // 도우미 호출은 결과를 기다려야 해서 별도 스레드에서 한다.
        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                handle(app)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun handle(context: Context) {
        val updated = ShutterSound.systemUpdated(context)
        when {
            // 업데이트로 설정 키 자체가 사라졌다면 앱을 고쳐야 한다.
            !ShutterSound.isSupported(context) -> {
                Notifier.notice(context, context.getString(R.string.err_key_blocked))
                Notifier.github(context)
            }
            HelperClient.isUsable(context) -> {
                val result = HelperClient.apply(context, silent = true)
                if (result.ok) {
                    if (updated) {
                        ShutterSound.rememberSystemVersion(context)
                        Notifier.notice(context, context.getString(R.string.notice_kept_after_update))
                    }
                } else {
                    // 도우미가 쓰지 못한다: 믿을 수 없는 상태로 표시하고 무선 디버깅 방식으로 원인을 가린다.
                    ShutterSound.setHelperBroken(context, true)
                    fallbackToAdb(context, updated)
                }
            }
            else -> legacyApply(context, updated)
        }
    }

    /** 도우미가 없을 때: 예전 방식(무선 디버깅). */
    private fun legacyApply(context: Context, updated: Boolean) {
        when {
            ShutterSound.isSilent(context) -> if (updated) {
                ShutterSound.rememberSystemVersion(context)
                Notifier.notice(context, context.getString(R.string.notice_kept_after_update))
            }
            else -> fallbackToAdb(context, updated)
        }
    }

    private fun fallbackToAdb(context: Context, updated: Boolean) {
        val ready = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED && !ShutterSound.needsRepair(context)
        if (ready) {
            SetupService.start(context, SetupService.ACTION_APPLY, silent = true, updated = updated, auto = true)
        } else {
            Notifier.notice(context, context.getString(R.string.notice_need_resetup))
            Notifier.github(context)
        }
    }
}
