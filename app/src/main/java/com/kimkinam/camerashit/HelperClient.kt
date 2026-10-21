package com.kimkinam.camerashit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * `targetSdk 22` 도우미 앱(:helper)을 호출한다. 도우미는 구형 앱이라 셔터음 설정 키에 직접 쓸 수 있어서
 * Wi-Fi/무선 디버깅 없이 즉시 켜고 끌 수 있다. 도우미는 본 앱의 signature 권한이 있는 호출만 받는다.
 */
object HelperClient {
    const val PKG = "com.kimkinam.camerashit.helper"

    /** assets/helper.apk 의 versionCode. :helper 의 versionCode 를 올리면 같이 올려야 한다. */
    const val BUNDLED_VERSION = 1L

    private const val ACTION_SET = "$PKG.SET"
    private const val RESULT_FAILED = 2

    data class Result(val ok: Boolean, val message: String? = null)

    private val handler: Handler by lazy {
        Handler(HandlerThread("helper-result").also { it.start() }.looper)
    }

    fun installedVersion(context: Context): Long? = try {
        context.packageManager.getPackageInfo(PKG, 0).longVersionCode
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    fun isInstalled(context: Context): Boolean = installedVersion(context) != null

    /** 도우미를 쓸 수 있는 상태(설치되어 있고 이전에 고장 난 적이 없음). */
    fun isUsable(context: Context): Boolean = isInstalled(context) && !ShutterSound.helperBroken(context)

    /**
     * 도우미에게 값을 쓰게 하고 실제로 반영됐는지 읽어서 확인한다. **백그라운드 스레드에서만** 호출한다.
     * 도우미가 막 설치되어 처음 깨어날 때는 결과 콜백이 비어서 올 수 있어서, 값을 직접 읽어 판단한다.
     */
    fun apply(context: Context, silent: Boolean, timeoutMs: Long = 4_000): Result {
        if (!isInstalled(context)) return Result(false, "helper not installed")
        val app = context.applicationContext
        val latch = CountDownLatch(1)
        var code = 0
        var data: String? = null
        val request = Intent(ACTION_SET)
            .setClassName(PKG, "$PKG.ControlReceiver")
            .putExtra("silent", silent)
        app.sendOrderedBroadcast(request, null, object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                code = resultCode
                data = resultData
                latch.countDown()
            }
        }, handler, 0, null, null)
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        if (code == RESULT_FAILED) return Result(false, data)

        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (ShutterSound.isSilent(app) != silent && SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(100)
        }
        return if (ShutterSound.isSilent(app) == silent) {
            ShutterSound.setWantSilent(app, silent)
            Result(true)
        } else {
            Result(false, data ?: "no response from helper")
        }
    }
}
