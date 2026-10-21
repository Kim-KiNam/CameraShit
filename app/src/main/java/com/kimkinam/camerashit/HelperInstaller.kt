package com.kimkinam.camerashit

import android.content.Context
import android.os.Process
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * 도우미 앱 설치/복구. 이미 열려 있는 온디바이스 adb(shell) 연결로 `pm install --bypass-low-target-sdk-block` 를
 * 실행한다. 구형(targetSdk 22) 앱은 Android 14+ 에서 이 우회 플래그가 있어야 설치되고, 이 플래그는 shell 만 쓸 수 있다.
 */
object HelperInstaller {
    private const val TAG = "CameraShit"

    /** 설치되어 있지 않거나, 내장 버전보다 낮거나, 이전에 고장 난 적이 있으면 (재)설치가 필요하다. */
    fun needsInstall(context: Context): Boolean {
        val installed = HelperClient.installedVersion(context) ?: return true
        return installed < HelperClient.BUNDLED_VERSION || ShutterSound.helperBroken(context)
    }

    /**
     * 필요하면 도우미를 (재)설치하고 권한을 허용한 뒤, 실제로 값을 쓸 수 있는지 한 번 시험한다.
     * 핵심 기능이 아니라서 실패해도 예외를 던지지 않고(대체 경로가 있다) 결과만 돌려준다.
     */
    fun ensure(context: Context, manager: AbsAdbConnectionManager, silent: Boolean): Boolean {
        return try {
            if (needsInstall(context)) install(context, manager)
            // 설치 직후 첫 호출로 깨우고, 쓰기가 되는지(= 도우미가 쓸 수 있는지) 확인한다.
            val result = HelperClient.apply(context, silent)
            ShutterSound.setHelperBroken(context, !result.ok)
            if (!result.ok) Log.w(TAG, "helper write failed: ${result.message}")
            result.ok
        } catch (t: Throwable) {
            Log.w(TAG, "helper setup failed", t)
            false
        }
    }

    private fun install(context: Context, manager: AbsAdbConnectionManager) {
        val bytes = context.assets.open("helper.apk").use { it.readBytes() }
        // 이 폰에는 다른 사용자 프로필(150)이 있어서 사용자를 지정하지 않으면 shell 이 거기 접근하다 실패한다.
        val user = Process.myUid() / 100000
        // exec: 는 pty 없이 바이너리를 그대로 주고받는다(shell: 은 줄바꿈 변환으로 APK 가 깨질 수 있다).
        val out = run(manager, "exec:pm install --user $user -r --bypass-low-target-sdk-block -S ${bytes.size}", bytes)
        check(out.contains("Success")) { "helper install failed: ${out.trim()}" }
        // 구형 앱의 '시스템 설정 수정'은 설치 때 허용되지만, 꺼져 있을 수 있어서 한 번 더 확실히 허용한다.
        run(manager, "shell:appops set --user $user ${HelperClient.PKG} WRITE_SETTINGS allow", null)
        // 오래 안 써도 휴면/권한 삭제 대상이 되지 않게 한다(부팅 신호를 계속 받아야 한다).
        run(manager, "shell:appops set --user $user ${HelperClient.PKG} AUTO_REVOKE_PERMISSIONS_IF_UNUSED ignore", null)
        Log.i(TAG, "helper installed: ${out.trim()}")
    }

    private fun run(manager: AbsAdbConnectionManager, destination: String, stdin: ByteArray?): String {
        val stream = manager.openStream(destination)
        val out = ByteArrayOutputStream()
        try {
            if (stdin != null) {
                val os = stream.openOutputStream()
                os.write(stdin)
                os.flush()
            }
            val input = stream.openInputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
        } catch (e: IOException) {
            // 원격이 명령을 끝내고 스트림을 닫은 정상 종료. 그 외 오류만 던진다.
            if (!stream.isClosed) throw e
        } finally {
            runCatching { stream.close() }
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
