package com.kimkinam.camerashit

import android.content.Context
import android.provider.Settings

/**
 * 삼성 One UI 의 카메라 강제 셔터음 설정(Settings.System). 값 0 = 끔, 1 = 기본(켬).
 *
 * 읽기는 누구나 가능하지만 쓰기는 일반 앱이 거부당한다(설정 제공자가 IllegalArgumentException).
 * 그래서 쓰기는 온디바이스 adb(shell 권한)가 [SetupService] 에서 수행한다.
 */
object ShutterSound {
    const val KEY = "csc_pref_camera_forced_shuttersound_key"
    private const val PREFS = "camerashit"
    private const val PREF_WANT_SILENT = "want_silent"
    private const val PREF_LAST_RESULT = "last_result"

    /** 현재 시스템 값이 무음(0)인지. */
    fun isSilent(context: Context): Boolean =
        Settings.System.getInt(context.contentResolver, KEY, 1) == 0

    /** 이 기기에 해당 키가 존재하는지(삼성 여부 판단용). */
    fun isSupported(context: Context): Boolean =
        Settings.System.getString(context.contentResolver, KEY) != null

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 사용자가 원하는 상태. 부팅 후 값이 풀렸을 때 재적용 기준이 된다. */
    fun wantSilent(context: Context): Boolean = prefs(context).getBoolean(PREF_WANT_SILENT, false)

    fun setWantSilent(context: Context, silent: Boolean) {
        prefs(context).edit().putBoolean(PREF_WANT_SILENT, silent).apply()
    }

    /** 서비스가 마지막으로 남긴 결과 문구(앱 화면 표시용). */
    fun lastResult(context: Context): String? = prefs(context).getString(PREF_LAST_RESULT, null)

    fun setLastResult(context: Context, value: String?) {
        prefs(context).edit().putString(PREF_LAST_RESULT, value).apply()
    }
}
