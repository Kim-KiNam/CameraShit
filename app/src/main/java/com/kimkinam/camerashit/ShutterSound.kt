package com.kimkinam.camerashit

import android.content.Context
import android.provider.Settings

/**
 * 삼성 One UI 의 카메라 강제 셔터음 설정(Settings.System)을 읽고 쓴다.
 * 값 0 = 셔터음 끔, 1 = 기본(켬).
 */
object ShutterSound {
    private const val KEY = "csc_pref_camera_forced_shuttersound_key"
    private const val PREFS = "camerashit"
    private const val PREF_WANT_SILENT = "want_silent"

    fun canWrite(context: Context): Boolean = Settings.System.canWrite(context)

    /** 현재 시스템 값이 무음(0)인지. 키가 없는 기기는 false. */
    fun isSilent(context: Context): Boolean =
        Settings.System.getInt(context.contentResolver, KEY, 1) == 0

    /** 이 기기에 해당 키가 존재하는지(삼성 여부 판단용). */
    fun isSupported(context: Context): Boolean =
        Settings.System.getString(context.contentResolver, KEY) != null

    /** 사용자가 원하는 상태. 부팅 시 재적용에 쓴다. */
    fun wantSilent(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREF_WANT_SILENT, false)

    /** 값을 쓰고 다시 읽어 반영됐는지 검증한다. 성공하면 true. */
    fun setSilent(context: Context, silent: Boolean): Boolean {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_WANT_SILENT, silent).apply()
        if (!canWrite(context)) return false
        return try {
            Settings.System.putInt(context.contentResolver, KEY, if (silent) 0 else 1)
            lastError = null
            isSilent(context) == silent
        } catch (e: Exception) {
            android.util.Log.e("CameraShit", "putInt failed", e)
            val target = context.applicationInfo.targetSdkVersion
            lastError = "target=$target ${e.javaClass.simpleName}: ${e.message}"
            false
        }
    }

    /** 마지막 쓰기 실패 원인(진단용). */
    var lastError: String? = null
        private set

    /** 저장된 원하는 상태를 시스템에 다시 적용한다(부팅/업데이트 후). */
    fun reapply(context: Context) {
        if (wantSilent(context) && canWrite(context) && !isSilent(context)) {
            setSilent(context, true)
        }
    }
}
