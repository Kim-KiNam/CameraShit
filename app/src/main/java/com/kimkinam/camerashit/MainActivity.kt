package com.kimkinam.camerashit

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
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
            SetupService.start(this, SetupService.ACTION_APPLY, silent = checked)
        }
    }

    /** 설정 앱에 다녀온 뒤에도 이 함수가 다시 불려서 자동으로 다음 단계로 넘어간다. */
    override fun onResume() {
        super.onResume()
        refresh()
    }

    /** 권한이 있고 페어링도 유효해야 \'설정 완료\'. 페어링이 풀렸으면 다시 1~3단계를 보여준다. */
    private fun isSetUp() =
        checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED &&
            !ShutterSound.needsRepair(this)

    private fun developerOptionsOn() =
        Settings.Global.getInt(contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

    private fun wirelessDebuggingOn() = Settings.Global.getInt(contentResolver, "adb_wifi_enabled", 0) == 1

    private fun refresh() {
        val supported = ShutterSound.isSupported(this)
        val setUp = isSetUp()
        if (setUp && ShutterSound.isSilent(this)) ShutterSound.rememberSystemVersionIfMissing(this)
        updating = true
        toggle.isChecked = supported && ShutterSound.isSilent(this)
        updating = false
        toggle.visibility = if (setUp && supported) View.VISIBLE else View.GONE
        status.setText(
            when {
                !supported -> R.string.status_unsupported
                ShutterSound.isSilent(this) -> R.string.status_silent
                else -> R.string.status_normal
            }
        )
        if (setUp || !supported) {
            hideStep()
        } else {
            showStep()
        }
        val last = ShutterSound.lastResult(this)
        result.text = last.orEmpty()
        result.visibility = if (last.isNullOrEmpty()) View.GONE else View.VISIBLE
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
                stepAction = { openWirelessDebugging() }
            }
            else -> {
                stepTitle.setText(R.string.step3_title)
                guide.setText(R.string.step3_guide)
                stepButton.setText(R.string.step_open_wireless)
                stepAction = {
                    if (ensurePermissions()) openWirelessDebugging()
                }
                // 3단계에 들어오면 페어링 대기(알림창 코드 입력)를 바로 시작한다.
                if (ensurePermissions() && !SetupService.pairing) {
                    SetupService.start(this, SetupService.ACTION_PAIR)
                }
            }
        }
    }

    /** 알림(코드 입력)과 Android 17 로컬 네트워크 권한이 있는지 확인하고, 없으면 요청한다. */
    private fun ensurePermissions(): Boolean {
        val missing = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= 37) add("android.permission.ACCESS_LOCAL_NETWORK")
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) return true
        requestPermissions(missing.toTypedArray(), REQ_PERMISSIONS)
        return false
    }

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
                startActivity(intent)
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
