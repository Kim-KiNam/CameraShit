package com.kimkinam.camerashit

import android.content.Context
import android.os.Build
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
    private const val PREF_LAST_FAILED = "last_failed"
    private const val PREF_HELPER_BROKEN = "helper_broken"
    private const val PREF_FINGERPRINT = "fingerprint"
    private const val PREF_NEEDS_REPAIR = "needs_repair"

    /** 현재 시스템 값이 무음(0)인지. */
    fun isSilent(context: Context): Boolean =
        Settings.System.getInt(context.contentResolver, KEY, 1) == 0

    /**
     * 지원 기기(삼성)인지 제조사로 판단한다. 설정 키가 있는지로 판단하면 안 된다: 삼성 폰도 이 값을 한 번도
     * 바꾼 적이 없으면 키가 아직 만들어지지 않은 것이 정상이고(없으면 기본값 1), 값을 쓰는 순간 생긴다.
     */
    @Suppress("UNUSED_PARAMETER")
    fun isSupported(context: Context): Boolean = Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    /** 지원하지 않는다는 안내에 보여줄 기기 이름(제조사 + 모델). */
    fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 사용자가 원하는 상태. 부팅 후 값이 풀렸을 때 재적용 기준이 된다. */
    fun wantSilent(context: Context): Boolean = prefs(context).getBoolean(PREF_WANT_SILENT, false)

    fun setWantSilent(context: Context, silent: Boolean) {
        prefs(context).edit().putBoolean(PREF_WANT_SILENT, silent).apply()
    }

    /** 마지막으로 설정에 성공했을 때의 펌웨어. 부팅 때 달라졌으면 시스템 업데이트가 있었다는 뜻이다. */
    fun rememberSystemVersion(context: Context) {
        prefs(context).edit().putString(PREF_FINGERPRINT, android.os.Build.FINGERPRINT).apply()
    }

    /** 이미 설정된 기기가 앱을 업데이트해서 저장값이 없을 때 현재 펌웨어를 기준으로 삼는다. */
    fun rememberSystemVersionIfMissing(context: Context) {
        if (prefs(context).getString(PREF_FINGERPRINT, null) == null) rememberSystemVersion(context)
    }

    /** 저장된 펌웨어가 있고 지금과 다르면 업데이트된 것. 저장값이 없으면(최초) 업데이트로 보지 않는다. */
    fun systemUpdated(context: Context): Boolean {
        val saved = prefs(context).getString(PREF_FINGERPRINT, null) ?: return false
        return saved != android.os.Build.FINGERPRINT
    }

    /** 페어링이 풀려 코드 입력부터 다시 해야 하는 상태(큰 업데이트 후 등). */
    fun needsRepair(context: Context): Boolean = prefs(context).getBoolean(PREF_NEEDS_REPAIR, false)

    fun setNeedsRepair(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(PREF_NEEDS_REPAIR, value).apply()
    }

    /** 도우미가 쓰기에 실패한 적이 있어 믿을 수 없는 상태(대체 경로 사용, 재설치 대상). */
    fun helperBroken(context: Context): Boolean = prefs(context).getBoolean(PREF_HELPER_BROKEN, false)

    fun setHelperBroken(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(PREF_HELPER_BROKEN, value).apply()
    }

    /** 서비스가 마지막으로 남긴 결과 문구(앱 화면 표시용). */
    fun lastResult(context: Context): String? = prefs(context).getString(PREF_LAST_RESULT, null)

    fun lastResultFailed(context: Context): Boolean = prefs(context).getBoolean(PREF_LAST_FAILED, false)

    fun setLastResult(context: Context, value: String?, failed: Boolean = false) {
        prefs(context).edit().putString(PREF_LAST_RESULT, value).putBoolean(PREF_LAST_FAILED, failed).apply()
    }
}
