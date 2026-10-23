package com.kimkinam.camerashit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * `targetSdk 22` 도우미 앱(:helper)을 호출한다. 도우미는 구형 앱이라 셔터음 설정 키에 직접 쓸 수 있어서
 * Wi-Fi/무선 디버깅 없이 즉시 켜고 끌 수 있다. 도우미는 본 앱의 signature 권한이 있는 호출만 받는다.
 */
object HelperClient {
    const val PKG = "com.kimkinam.camerashit.helper"

    /** assets/helper.apk 의 versionCode. :helper 의 versionCode 를 올리면 같이 올려야 한다. */
    const val BUNDLED_VERSION = 2L

    private const val ACTION_SET = "$PKG.SET"
    private const val RESULT_OK = 1
    private const val RESULT_FAILED = 2

    data class Result(val ok: Boolean, val message: String? = null)

    private val handler: Handler by lazy {
        Handler(HandlerThread("helper-result").also { it.start() }.looper)
    }

    /** 켜기/끄기 요청을 한 줄로 세운다. 빠르게 연타해도 요청과 결과가 서로 엇갈리지 않는다. */
    private val serial = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun installedVersion(context: Context): Long? = try {
        context.packageManager.getPackageInfo(PKG, 0).longVersionCode
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    fun isInstalled(context: Context): Boolean = installedVersion(context) != null

    /** 도우미를 쓸 수 있는 상태(설치되어 있고 이전에 고장 난 적이 없음). */
    fun isUsable(context: Context): Boolean = isInstalled(context) && !ShutterSound.helperBroken(context)

    /** [apply]를 순서대로 실행하고 결과를 메인 스레드로 돌려준다. */
    fun applyAsync(context: Context, silent: Boolean, onResult: (Result) -> Unit) {
        val app = context.applicationContext
        serial.execute {
            val result = apply(app, silent)
            main.post { onResult(result) }
        }
    }

    /**
     * 도우미에게 값을 쓰게 하고 **도우미가 처리했다는 응답**을 확인한다. **백그라운드 스레드에서만** 호출한다.
     *
     * 값만 보고 성공이라고 하면 안 된다: 직전에 다른 경로(adb)가 이미 같은 값을 써 둔 경우, 도우미가 아무것도
     * 못 해도 값은 맞기 때문이다. 그래서 도우미의 명시적 응답을 요구한다. 도우미가 막 설치되어 처음 깨어날 때는
     * 응답이 비어 올 수 있어서 한 번만 다시 보낸다. 그래도 응답이 없으면 "값이 이번 호출로 바뀐 경우"에만
     * 성공으로 본다(바뀌었다면 도우미가 쓴 것이다).
     */
    fun apply(context: Context, silent: Boolean, timeoutMs: Long = 4_000): Result {
        if (!isInstalled(context)) return Result(false, "helper not installed")
        val app = context.applicationContext
        val before = ShutterSound.isSilent(app)

        var ack = send(app, silent, timeoutMs)
        if (ack.code == 0) {
            Thread.sleep(500)
            ack = send(app, silent, timeoutMs)
        }
        if (ack.code == RESULT_FAILED) return Result(false, ack.data)

        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (ShutterSound.isSilent(app) != silent && SystemClock.elapsedRealtime() < deadline) {
            Thread.sleep(100)
        }
        val now = ShutterSound.isSilent(app)
        val acknowledged = ack.code == RESULT_OK
        val changedByHelper = before != silent && now == silent
        return if (now == silent && (acknowledged || changedByHelper)) {
            ShutterSound.setWantSilent(app, silent)
            Result(true)
        } else {
            Result(
                false,
                if (acknowledged) "helper answered but the value did not change (silent=$now)" else ack.data ?: "no response from helper",
            )
        }
    }

    private class Ack(val code: Int, val data: String?)

    private fun send(app: Context, silent: Boolean, timeoutMs: Long): Ack {
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
        return Ack(code, data)
    }
}
