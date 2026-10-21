package com.kimkinam.camerashit

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.android.AdbMdns
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * 온디바이스 무선 디버깅으로 셔터 설정을 쓰는 포그라운드 서비스.
 *
 * - [ACTION_PAIR]: 페어링 포트를 mDNS 로 찾고, 알림창에서 6자리 코드를 받아 페어링 → 적용 → 권한 부여.
 * - [ACTION_CODE]: 알림의 인라인 입력(코드) 처리.
 * - [ACTION_APPLY]: 이미 페어링/권한이 있을 때 코드 없이 켜기/끄기(복원) 적용. 무선 디버깅은 앱이 직접 켜고 끈다.
 */
class SetupService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private var mdns: AdbMdns? = null

    @Volatile
    private var pairingHost: InetAddress? = null

    @Volatile
    private var pairingPort = -1

    @Volatile
    private var destroyed = false

    /**
     * 처리 중이거나 줄 서 있는 작업 수. 0 이 되기 전에는 서비스를 끝내지 않는다
     * (끝내면서 줄 서 있던 작업을 강제로 끊어 InterruptedException 이 나던 문제를 막는다).
     */
    private val pending = java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile
    private var queuedSilent: Boolean? = null

    /** 작업 하나가 끝났음을 알리고, 더 남은 작업이 없으면 true. */
    private fun release(): Boolean {
        val remaining = pending.updateAndGet { maxOf(0, it - 1) }
        if (remaining == 0) queuedSilent = null
        return remaining == 0
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        when (intent?.action) {
            ACTION_PAIR -> startPairing()
            ACTION_CODE -> {
                val code = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_CODE)?.toString()?.trim().orEmpty()
                handleCode(code)
            }
            ACTION_APPLY -> applyWithoutPairing(
                intent.getBooleanExtra(EXTRA_SILENT, true),
                intent.getBooleanExtra(EXTRA_UPDATED, false),
                intent.getBooleanExtra(EXTRA_AUTO, false),
            )
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        destroyed = true
        pairing = false
        mdns?.stop()
        executor.shutdownNow()
        super.onDestroy()
    }

    // ---- 페어링 흐름 -------------------------------------------------------------------------

    private fun startPairing() {
        pairing = true
        show(
            getString(R.string.notif_pair_title),
            getString(R.string.notif_pair_waiting),
            withInput = true,
        )
        mdns?.stop()
        mdns = AdbMdns(this, AdbMdns.SERVICE_TYPE_TLS_PAIRING) { host, port ->
            pairingHost = host
            pairingPort = port
            if (port > 0) {
                show(
                    getString(R.string.notif_pair_title),
                    getString(R.string.notif_pair_ready),
                    withInput = true,
                )
            }
        }.also { it.start() }
    }

    private fun handleCode(code: String) {
        if (!Regex("\\d{6}").matches(code)) {
            show(getString(R.string.notif_pair_title), getString(R.string.notif_bad_code), withInput = true)
            return
        }
        val host = pairingHost
        val port = pairingPort
        if (host == null || port <= 0) {
            show(getString(R.string.notif_pair_title), getString(R.string.notif_no_pairing_screen), withInput = true)
            return
        }
        pending.incrementAndGet()
        executor.execute {
            var step = "init"
            try {
                step = "pair"
                show(getString(R.string.notif_working_title), getString(R.string.notif_pairing))
                val manager = AdbManager.get(this)
                manager.pair(host.hostAddress!!, port, code)
                mdns?.stop()
                step = "connect"
                show(getString(R.string.notif_working_title), getString(R.string.notif_connecting))
                connect(manager)
                step = "apply"
                show(getString(R.string.notif_working_title), getString(R.string.notif_applying))
                writeSilent(manager, silent = true, grantSelf = true)
                finish(getString(R.string.result_done))
            } catch (t: KeyBlockedException) {
                Log.e(TAG, "key blocked", t)
                finish(getString(R.string.err_key_blocked), failed = true)
            } catch (t: Throwable) {
                Log.e(TAG, "setup failed at $step", t)
                finish(getString(R.string.result_failed, "[$step] ${describe(t)}"), failed = true)
            }
        }
    }

    // ---- 재적용/복원(페어링과 권한이 이미 있을 때) ---------------------------------------------

    /**
     * @param auto 사용자가 누른 게 아니라 부팅/예약 작업으로 시작된 경우. Wi-Fi 가 아직 없으면 실패로 알리는 대신
     *             잠깐 기다리고, 그래도 없으면 Wi-Fi 가 연결될 때 자동으로 다시 시도하도록 예약한다.
     */
    private fun applyWithoutPairing(silent: Boolean, updated: Boolean, auto: Boolean) {
        // startForegroundService 로 시작했으므로 중복 요청이어도 반드시 startForeground 는 호출해야 한다.
        show(getString(R.string.notif_working_title), getString(R.string.notif_connecting))
        synchronized(this) {
            // 같은 목표 상태의 요청이 이미 처리 중이거나 줄 서 있으면 겹치는 요청은 버린다.
            if (pending.get() > 0 && queuedSilent == silent) return
            queuedSilent = silent
            pending.incrementAndGet()
        }
        executor.execute {
            try {
                if (!Wifi.isConnected(this) && !(auto && waitForWifi())) {
                    if (auto) {
                        deferUntilWifi()
                    } else {
                        // 사용자가 직접 누른 경우는 오래 기다리지 말고 바로 알린다(버그가 아니므로 GitHub 안내는 생략).
                        finish(getString(R.string.err_no_wifi), failed = true, github = false)
                    }
                    return@execute
                }
                if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) {
                    throw IllegalStateException(getString(R.string.err_need_setup))
                }
                // 무선 디버깅은 앱이 직접 켠다. 끝나면 다시 끈다.
                Settings.Global.putInt(contentResolver, ADB_WIFI_ENABLED, 1)
                val manager = AdbManager.get(this)
                connect(manager)
                writeSilent(manager, silent, grantSelf = false)
                finish(
                    getString(
                        when {
                            !silent -> R.string.result_restored
                            updated -> R.string.result_reapplied
                            else -> R.string.result_done
                        }
                    )
                )
            } catch (t: KeyBlockedException) {
                Log.e(TAG, "key blocked", t)
                finish(getString(R.string.err_key_blocked), failed = true)
            } catch (t: AdbPairingRequiredException) {
                // 업데이트 등으로 페어링이 풀림: 코드 입력부터 다시 해야 한다.
                Log.e(TAG, "pairing lost", t)
                ShutterSound.setNeedsRepair(this, true)
                finish(getString(R.string.err_repair), failed = true)
            } catch (t: Throwable) {
                Log.e(TAG, "apply failed", t)
                finish(getString(R.string.result_failed, describe(t)), failed = true)
            }
        }
    }

    // ---- 공통 --------------------------------------------------------------------------------

    /** 부팅 직후에는 Wi-Fi 가 몇 초 늦게 붙으므로 잠깐 기다린다. 연결되면 true. */
    private fun waitForWifi(): Boolean {
        val deadline = SystemClock.elapsedRealtime() + WIFI_WAIT_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            if (destroyed) return false
            if (Wifi.isConnected(this)) return true
            Thread.sleep(2_000)
        }
        return Wifi.isConnected(this)
    }

    /** 기다려도 Wi-Fi 가 없을 때: 연결되는 순간 자동으로 다시 시도하도록 예약하고, 안내는 한 번만 한다. */
    private fun deferUntilWifi() {
        val firstTime = WifiRetryJob.schedule(this)
        if (firstTime) Notifier.notice(this, getString(R.string.notice_pending_wifi))
        if (release()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /**
     * 무선 디버깅을 막 켠 직후(특히 부팅 직후 Wi-Fi 안정 전)에는 mDNS 로 주소를 찾는 데 시간이 걸려
     * 타임아웃(InterruptedException)이 날 수 있어서 몇 번 재시도한다. 서비스가 종료되는 중이면 중단한다.
     */
    private fun connect(manager: AbsAdbConnectionManager) {
        manager.setTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        var lastError: InterruptedException? = null
        repeat(CONNECT_ATTEMPTS) { attempt ->
            if (attempt > 0) Thread.sleep(CONNECT_RETRY_DELAY_MS)
            try {
                if (manager.autoConnect(this, 15_000) || manager.isConnected) return
            } catch (e: InterruptedException) {
                if (destroyed) throw e
                lastError = e
            }
        }
        lastError?.let { throw it }
        throw IllegalStateException(getString(R.string.err_connect))
    }

    /** 연결과 명령 실행은 됐는데 설정 키가 사라졌거나 값이 바뀌지 않는 경우(제조사가 막은 것으로 본다). */
    private class KeyBlockedException(message: String) : Exception(message)

    private fun writeSilent(manager: AbsAdbConnectionManager, silent: Boolean, grantSelf: Boolean) {
        val put = sh(manager, "settings put system ${ShutterSound.KEY} ${if (silent) 0 else 1}")
        if (put.contains("Exception") || put.contains("Error")) throw KeyBlockedException(put.trim())
        exemptFromAutoRevoke(manager)
        if (grantSelf) {
            val user = Process.myUid() / 100000
            val output = sh(manager, "pm grant --user $user $packageName android.permission.WRITE_SECURE_SETTINGS")
            check(!output.contains("Exception") && !output.contains("Error")) { output.trim() }
        }
        ShutterSound.setWantSilent(this, silent)
        if (ShutterSound.isSilent(this) != silent) throw KeyBlockedException(getString(R.string.err_verify))
        ShutterSound.setNeedsRepair(this, false)
        ShutterSound.rememberSystemVersion(this)
        // 이 연결이 열려 있는 김에 도우미를 설치/복구한다. 이후 켜기/끄기·재부팅 재적용은 Wi-Fi/adb 없이 도우미가 한다.
        HelperInstaller.ensure(this, manager, silent)
    }

    /**
     * \'사용하지 않는 앱 관리\'(오래 안 쓴 앱의 권한 삭제/휴면)에서 이 앱을 제외한다.
     * 휴면 상태가 되면 재부팅 신호를 못 받아 자동 재적용이 안 되기 때문이다. 핵심 기능이 아니라서 실패해도 무시한다.
     */
    private fun exemptFromAutoRevoke(manager: AbsAdbConnectionManager) {
        runCatching {
            val user = Process.myUid() / 100000
            sh(manager, "appops set --user $user $packageName AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore")
        }.onFailure { Log.w(TAG, "auto-revoke exemption failed", it) }
    }

    /** shell 명령을 실행하고 출력 전체를 돌려준다. libadb 는 스트림 끝을 -1 이 아닌 IOException 으로 알린다. */
    private fun sh(manager: AbsAdbConnectionManager, command: String): String {
        val stream = manager.openStream("shell:$command")
        val out = java.io.ByteArrayOutputStream()
        try {
            val input = stream.openInputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
        } catch (e: java.io.IOException) {
            // 원격이 명령을 끝내고 스트림을 닫은 정상 종료. 그 외 오류만 던진다.
            if (!stream.isClosed) throw e
        } finally {
            runCatching { stream.close() }
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun finish(message: String, failed: Boolean = false, github: Boolean = failed) {
        // 보안상 무선 디버깅은 항상 끈다. 권한이 있으면 직접, 없으면 연결이 살아있을 때 shell 로.
        runCatching {
            if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED) {
                Settings.Global.putInt(contentResolver, ADB_WIFI_ENABLED, 0)
            }
        }
        runCatching { AdbManager.get(this).disconnect() }
        // 이번 시도로 끝났으므로 Wi-Fi 대기용 예약 작업은 더 필요 없다.
        WifiRetryJob.cancel(this)
        ShutterSound.setLastResult(this, message, failed)
        // 줄 서 있는 다른 작업이 있으면 포그라운드/서비스는 유지하고, 마지막 작업이 끝날 때만 정리한다.
        val idle = release()
        if (idle) stopForeground(STOP_FOREGROUND_REMOVE)
        if (failed) {
            // 실패는 읽을 시간이 필요하므로 일반 중요도로 잠시 보여주고 자동으로 사라진다.
            Notifier.notice(this, message)
        } else {
            // 성공은 조용한 채널(소리/팝업 없음)로 잠깐만 보여주고 자동으로 사라진다.
            notificationManager().notify(
                ID_RESULT,
                builder(getString(R.string.app_name), message, CHANNEL_PROGRESS, NotificationManager.IMPORTANCE_LOW)
                    .setAutoCancel(true)
                    .setTimeoutAfter(Notifier.TIMEOUT_SUCCESS_MS)
                    .build()
            )
        }
        // 실패하면 별도 알림으로 GitHub 주소를 함께 안내한다.
        if (github) Notifier.github(this)
        if (idle) stopSelf()
    }

    private fun describe(t: Throwable): String = "${t.javaClass.simpleName}: ${t.message}"

    private fun notificationManager() = getSystemService(NotificationManager::class.java)

    private fun builder(title: String, text: String, channelId: String, importance: Int): Notification.Builder {
        val channelName = getString(if (channelId == CHANNEL_PAIR) R.string.channel_name else R.string.channel_progress_name)
        val channel = NotificationChannel(channelId, channelName, importance)
        notificationManager().createNotificationChannel(channel)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
    }

    /**
     * 포그라운드 서비스의 진행 알림. 코드 입력이 필요한 알림만 팝업(높은 중요도)으로 띄우고,
     * 나머지 진행 상황은 조용한 채널로 보여준다(서비스가 끝나면 사라진다).
     */
    private fun show(title: String, text: String, withInput: Boolean = false) {
        val builder = if (withInput) {
            builder(title, text, CHANNEL_PAIR, NotificationManager.IMPORTANCE_HIGH)
        } else {
            builder(title, text, CHANNEL_PROGRESS, NotificationManager.IMPORTANCE_LOW)
        }.setOngoing(true)
        if (withInput) {
            val remoteInput = RemoteInput.Builder(KEY_CODE).setLabel(getString(R.string.notif_input_hint)).build()
            val reply = PendingIntent.getService(
                this, 1,
                Intent(this, SetupService::class.java).setAction(ACTION_CODE),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val action = Notification.Action.Builder(
                Icon.createWithResource(this, R.drawable.ic_notification),
                getString(R.string.notif_input_action),
                reply
            ).addRemoteInput(remoteInput).build()
            builder.addAction(action)
        }
        startForeground(ID_FOREGROUND, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    }

    companion object {
        const val ACTION_PAIR = "com.kimkinam.camerashit.PAIR"
        const val ACTION_CODE = "com.kimkinam.camerashit.CODE"
        const val ACTION_APPLY = "com.kimkinam.camerashit.APPLY"
        const val EXTRA_SILENT = "silent"

        /** 페어링 대기 서비스가 떠 있는지(화면이 중복 시작하지 않도록). */
        @Volatile
        var pairing = false

        /** 서비스가 작업 중인지(앱을 열 때 자동 재적용이 겹쳐 시작되지 않도록). */
        @Volatile
        var running = false

        private const val TAG = "CameraShit"
        private const val CONNECT_ATTEMPTS = 3
        private const val CONNECT_RETRY_DELAY_MS = 3_000L
        private const val KEY_CODE = "code"
        private const val CHANNEL_PAIR = "setup"
        private const val CHANNEL_PROGRESS = "progress"
        private const val ID_FOREGROUND = 1
        private const val ID_RESULT = 2
        private const val ADB_WIFI_ENABLED = "adb_wifi_enabled"

        const val EXTRA_UPDATED = "updated"
        const val EXTRA_AUTO = "auto"
        private const val WIFI_WAIT_MS = 60_000L

        fun start(
            context: Context,
            action: String,
            silent: Boolean = true,
            updated: Boolean = false,
            auto: Boolean = false,
        ) {
            context.startForegroundService(
                Intent(context, SetupService::class.java).setAction(action)
                    .putExtra(EXTRA_SILENT, silent)
                    .putExtra(EXTRA_UPDATED, updated)
                    .putExtra(EXTRA_AUTO, auto)
            )
        }
    }
}
