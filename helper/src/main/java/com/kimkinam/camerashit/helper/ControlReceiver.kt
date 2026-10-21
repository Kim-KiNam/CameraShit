package com.kimkinam.camerashit.helper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * 셔터음 설정을 직접 쓰는 도우미.
 * - [ACTION_SET]: 본 앱이 보낸 요청. 쓰고 나서 다시 읽어 확인한 결과를 ordered broadcast 결과로 돌려준다.
 * - BOOT_COMPLETED: 사용자가 원하는 상태(끔)인데 값이 풀렸으면 Wi-Fi/adb 없이 바로 다시 쓴다.
 */
class ControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SET -> {
                val silent = intent.getBooleanExtra(EXTRA_SILENT, true)
                val message = write(context, silent)
                if (isOrderedBroadcast) {
                    setResult(if (message == null) RESULT_OK else RESULT_FAILED, message ?: "ok", null)
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                if (wantSilent(context) && currentValue(context) != 0) write(context, true)
            }
        }
    }

    /** 성공하면 null, 실패하면 이유. */
    private fun write(context: Context, silent: Boolean): String? = try {
        val value = if (silent) 0 else 1
        Settings.System.putInt(context.contentResolver, KEY, value)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(PREF_WANT_SILENT, silent).apply()
        if (currentValue(context) == value) null else "value did not change (now ${currentValue(context)})"
    } catch (e: Exception) {
        "${e.javaClass.simpleName}: ${e.message}"
    }

    private fun currentValue(context: Context) = Settings.System.getInt(context.contentResolver, KEY, 1)

    private fun wantSilent(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREF_WANT_SILENT, false)

    companion object {
        const val ACTION_SET = "com.kimkinam.camerashit.helper.SET"
        const val EXTRA_SILENT = "silent"
        const val RESULT_OK = 1
        const val RESULT_FAILED = 2
        private const val KEY = "csc_pref_camera_forced_shuttersound_key"
        private const val PREFS = "helper"
        private const val PREF_WANT_SILENT = "want_silent"
    }
}
