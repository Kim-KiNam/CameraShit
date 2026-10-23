package com.kimkinam.camerashit

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var stepTitle: TextView
    private lateinit var guide: TextView
    private lateinit var result: TextView
    private lateinit var toggle: Switch
    private lateinit var stepButton: Button
    private var updating = false

    /** 도우미에게 요청을 보내 기다리는 중(이 동안 스위치를 잠근다). */
    private var busy = false

    /** 지금 단계에서 stepButton 이 하는 일. */
    private var stepAction: () -> Unit = {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        status = findViewById(R.id.status)
        stepTitle = findViewById(R.id.step_title)
        guide = findViewById(R.id.guide)
        result = findViewById(R.id.result)
        toggle = findViewById(R.id.toggle)
        stepButton = findViewById(R.id.step_button)

        stepButton.setOnClickListener { stepAction() }
        toggle.setOnCheckedChangeListener { _, checked ->
            if (updating) return@setOnCheckedChangeListener
            applyChoice(checked)
        }
    }

    /**
     * 켜기/끄기. 도우미가 있으면 Wi-Fi/무선 디버깅 없이 즉시 처리하고, 없거나 실패하면
     * 예전 방식(온디바이스 무선 디버깅, Wi-Fi 필요)으로 대체한다.
     */
    private fun applyChoice(silent: Boolean) {
        if (HelperClient.isUsable(this)) {
            // 처리하는 동안 스위치를 잠가서 빠른 연타로 요청과 결과가 엇갈리지 않게 한다(요청도 한 줄로 처리된다).
            busy = true
            toggle.isEnabled = false
            val app = applicationContext
            HelperClient.applyAsync(app, silent) { result ->
                busy = false
                toggle.isEnabled = true
                if (result.ok) {
                    ShutterSound.setLastResult(
                        app,
                        getString(if (silent) R.string.result_done else R.string.result_restored),
                    )
                    refresh()
                } else {
                    ShutterSound.setHelperBroken(app, true)
                    fallbackApply(silent)
                }
            }
        } else {
            fallbackApply(silent)
        }
    }

    private fun fallbackApply(silent: Boolean) {
        // 대체 경로(무선 디버깅)도 쓸 수 없으면 설정을 다시 진행하도록 안내 화면으로 보낸다.
        if (!adbReady()) {
            ShutterSound.setNeedsRepair(this, true)
            refresh()
            return
        }
        // 무선 디버깅은 Wi-Fi 가 있어야 켜진다. 없으면 시작하지 않고 스위치를 되돌린다.
        if (!requireWifi()) {
            refresh()
            return
        }
        SetupService.start(this, SetupService.ACTION_APPLY, silent = silent)
    }

    private fun adbReady() =
        checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED &&
            !ShutterSound.needsRepair(this)

    /** 설정 앱에 다녀온 뒤에도 이 함수가 다시 불려서 자동으로 다음 단계로 넘어간다. */
    override fun onResume() {
        super.onResume()
        refresh()
    }

    /**
     * 도우미가 쓸 수 있거나, 대체 경로(권한 + 유효한 페어링)가 있으면 \'설정 완료\'.
     * 둘 다 없으면 다시 1~3단계를 보여준다.
     */
    private fun isSetUp() = HelperClient.isUsable(this) || adbReady()

    private fun developerOptionsOn() =
        Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

    private fun wirelessDebuggingOn() = Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1

    private fun refresh() {
        val supported = ShutterSound.isSupported(this)
        val setUp = isSetUp()
        if (setUp && ShutterSound.isSilent(this)) ShutterSound.rememberSystemVersionIfMissing(this)
        // 안전망: 원하는 상태(끔)인데 풀려 있으면 앱을 연 김에 자동으로 다시 적용한다.
        if (setUp && supported && ShutterSound.wantSilent(this) && !ShutterSound.isSilent(this) &&
            !SetupService.running && !busy
        ) {
            if (HelperClient.isUsable(this)) {
                busy = true
                val app = applicationContext
                HelperClient.applyAsync(app, silent = true) { result ->
                    busy = false
                    if (result.ok) refresh() else ShutterSound.setHelperBroken(app, true)
                }
            } else if (adbReady() && Wifi.isConnected(this)) {
                SetupService.start(this, SetupService.ACTION_APPLY, silent = true, auto = true)
            }
        } else if (setUp && supported && !SetupService.running && !busy && HelperInstaller.needsInstall(this) &&
            adbReady() && Wifi.isConnected(this) && ShutterSound.helperInstallDue(this)
        ) {
            // 도우미가 없거나(예전 버전에서 업데이트), 오래된 버전이거나, 고장 난 상태: 앱을 연 김에 하루 한 번만
            // 무선 디버깅 경로로 현재 상태를 다시 적용하면서 도우미를 설치/복구한다.
            ShutterSound.markHelperInstallAttempt(this)
            SetupService.start(this, SetupService.ACTION_APPLY, silent = ShutterSound.wantSilent(this), auto = true)
        }
        updating = true
        toggle.isChecked = supported && ShutterSound.isSilent(this)
        updating = false
        toggle.isEnabled = !busy
        toggle.visibility = if (setUp && supported) View.VISIBLE else View.GONE
        status.text = when {
            !supported -> getString(R.string.status_unsupported, ShutterSound.deviceName())
            // 설정값이 \'끔\'이라는 뜻이지 실제 촬영음을 확인한 것은 아니다. 소리 모드가 \'소리\'면 셔터음이 날 수 있어서 함께 알려준다.
            ShutterSound.isSilent(this) -> getString(R.string.status_silent) + "\n" + getString(
                if (getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                    R.string.status_ringer_sound
                } else {
                    R.string.status_ringer_ok
                }
            )
            else -> getString(R.string.status_normal)
        }
        if (setUp || !supported) {
            hideStep()
        } else {
            showStep()
        }
        // 지난 실패 기록은 원하는 상태가 이미 맞게 되어 있으면 더 보여주지 않는다.
        if (ShutterSound.lastResultFailed(this) && ShutterSound.isSilent(this) == ShutterSound.wantSilent(this)) {
            ShutterSound.setLastResult(this, null)
        }
        val last = ShutterSound.lastResult(this)
        result.text = last.orEmpty()
        result.visibility = if (last.isNullOrEmpty()) View.GONE else View.VISIBLE
        // 처음 설정이 끝난 뒤 처음 앱으로 돌아왔을 때 한 번만 \'설정 완료\'를 팝업으로 알린다.
        ShutterSound.takeSetupDonePending(this)?.let { message ->
            AlertDialog.Builder(this)
                .setTitle(R.string.setup_done_title)
                .setMessage(message)
                .setPositiveButton(R.string.alert_ok, null)
                .show()
        }
    }

    private fun hideStep() {
        stepTitle.visibility = View.GONE
        guide.visibility = View.GONE
        stepButton.visibility = View.GONE
    }

    private fun showStep() {
        stepTitle.visibility = View.VISIBLE
        guide.visibility = View.VISIBLE
        stepButton.visibility = View.VISIBLE
        when {
            !developerOptionsOn() -> {
                stepTitle.setText(R.string.step1_title)
                guide.setText(R.string.step1_guide)
                stepButton.setText(R.string.step1_button)
                stepAction = { openDeviceInfo() }
            }
            !wirelessDebuggingOn() -> {
                stepTitle.setText(R.string.step2_title)
                guide.setText(R.string.step2_guide)
                stepButton.setText(R.string.step_open_wireless)
                stepAction = { if (requireWifi()) openWirelessDebugging() }
            }
            missingPermissions().isNotEmpty() -> {
                // 권한은 자동으로 다시 묻지 않는다(거절하면 같은 팝업이 끝없이 반복될 수 있다). 버튼을 눌렀을 때만 요청한다.
                stepTitle.setText(R.string.step3_title)
                guide.setText(R.string.step_permission_guide)
                if (permissionBlockedForever()) {
                    stepButton.setText(R.string.step_permission_open_settings)
                    stepAction = { startFirst(appDetailsIntent()) }
                } else {
                    stepButton.setText(R.string.step_permission_button)
                    stepAction = { requestMissingPermissions() }
                }
            }
            else -> {
                stepTitle.setText(R.string.step3_title)
                guide.setText(R.string.step3_guide)
                stepButton.setText(R.string.step_open_wireless)
                stepAction = { openWirelessDebugging() }
                // 3단계에 들어오면 페어링 대기(알림창 코드 입력)를 바로 시작한다.
                if (!SetupService.pairing) {
                    SetupService.start(this, SetupService.ACTION_PAIR)
                }
            }
        }
    }

    /** Wi-Fi 가 연결되어 있으면 true. 아니면 안내 팝업을 띄우고 false. */
    private fun requireWifi(): Boolean {
        if (Wifi.isConnected(this)) return true
        AlertDialog.Builder(this)
            .setTitle(R.string.alert_wifi_title)
            .setMessage(R.string.alert_wifi_message)
            .setPositiveButton(R.string.alert_ok, null)
            .setNeutralButton(R.string.alert_open_wifi) { _, _ -> startFirst(Intent(Settings.ACTION_WIFI_SETTINGS)) }
            .show()
        return false
    }

    /** 설정에 필요한 권한 중 아직 허용되지 않은 것: 알림(코드 입력)과 Android 17 로컬 네트워크. 확인만 하고 요청은 하지 않는다. */
    private fun missingPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 37) add("android.permission.ACCESS_LOCAL_NETWORK")
    }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    /** 사용자가 버튼을 눌렀을 때만 호출한다. */
    private fun requestMissingPermissions() {
        val missing = missingPermissions()
        if (missing.isEmpty()) return
        ShutterSound.setPermissionAsked(this)
        requestPermissions(missing.toTypedArray(), REQ_PERMISSIONS)
    }

    /**
     * 이미 한 번 물었는데 시스템이 더는 팝업을 띄워 주지 않는 상태(\'다시 묻지 않음\' 포함).
     * 이 경우 요청해도 즉시 거절되므로 앱 설정 화면으로 보내야 한다.
     */
    private fun permissionBlockedForever(): Boolean =
        ShutterSound.permissionAsked(this) && missingPermissions().any { !shouldShowRequestPermissionRationale(it) }

    private fun appDetailsIntent() =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))

    private fun openDeviceInfo() = startFirst(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))

    /**
     * 개발자 옵션을 열고 \'무선 디버깅\' 항목으로 스크롤해 하이라이트한다(설정 앱의 fragment_args_key 기능).
     * SubSettings 등 내부 화면은 외부 앱이 직접 열 수 없어서 이 방식을 쓴다.
     */
    private fun openWirelessDebugging() = startFirst(
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .putExtra(SETTINGS_HIGHLIGHT_KEY, "toggle_adb_wireless"),
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
    )

    private fun startFirst(vararg intents: Intent) {
        for (intent in intents) {
            try {
                // 설정 화면을 별도 작업으로 연다. 같은 작업에 쌓으면 설정을 마치고 앱 아이콘을 눌러도
                // 앱 메인 화면이 아니라 맨 위의 설정 화면이 다시 나와서 \'설정 완료\' 안내를 놓치게 된다.
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) refresh()
    }

    private companion object {
        const val REQ_PERMISSIONS = 1
        const val SETTINGS_HIGHLIGHT_KEY = ":settings:fragment_args_key"
    }
}
