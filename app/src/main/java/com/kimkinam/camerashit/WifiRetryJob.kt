package com.kimkinam.camerashit

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

/**
 * 부팅 직후 Wi-Fi 가 없어서 셔터음 재적용을 못 했을 때, Wi-Fi 가 연결되는 순간 한 번 다시 시도한다.
 * 상시 실행 없이 시스템이 조건이 맞을 때만 깨운다. 재부팅해도 예약이 유지된다.
 * (도우미 앱이 있으면 이 경로는 거의 쓰이지 않는다. 도우미가 없는 기기의 대체 경로용이다.)
 */
class WifiRetryJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val needsApply = ShutterSound.wantSilent(this) && !ShutterSound.isSilent(this)
        if (needsApply && Wifi.isConnected(this)) {
            try {
                SetupService.start(this, SetupService.ACTION_APPLY, silent = true, auto = true)
            } catch (e: Exception) {
                // 백그라운드에서 포그라운드 서비스를 시작하지 못하는 기기/상태가 있다(Android 12+).
                // 실패로 두지 말고 사용자가 앱을 열면 자동으로 적용되도록 알려준다(앱을 열면 풀린 설정을 다시 적용한다).
                Log.w("CameraShit", "cannot start service from job", e)
                Notifier.notice(this, getString(R.string.notice_wifi_connected_open_app))
            }
        }
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val JOB_ID = 1001

        /** 예약한다. 이번에 새로 예약한 것이면 true(이미 예약되어 있었으면 false). */
        fun schedule(context: Context): Boolean {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val alreadyPending = scheduler.getPendingJob(JOB_ID) != null
            // 과금 여부와 상관없이 Wi-Fi 연결 자체를 조건으로 한다(종량제 Wi-Fi 나 핫스팟도 포함).
            val wifi = NetworkRequest.Builder()
                .clearCapabilities()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, WifiRetryJob::class.java))
                    .setRequiredNetwork(wifi)
                    .setPersisted(true)
                    .build()
            )
            return !alreadyPending
        }

        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
        }
    }
}
