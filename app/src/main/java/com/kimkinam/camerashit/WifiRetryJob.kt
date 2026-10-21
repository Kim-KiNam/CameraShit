package com.kimkinam.camerashit

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context

/**
 * 부팅 직후 Wi-Fi 가 없어서 셔터음 재적용을 못 했을 때, Wi-Fi(무과금 네트워크)가 연결되는 순간 한 번 다시 시도한다.
 * 상시 실행 없이 시스템이 조건이 맞을 때만 깨운다. 재부팅해도 예약이 유지된다.
 */
class WifiRetryJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val needsApply = ShutterSound.wantSilent(this) && !ShutterSound.isSilent(this)
        when {
            !needsApply -> Unit // 그 사이 이미 적용됨
            !Wifi.isConnected(this) -> schedule(this) // 과금 안 되는 모바일망 등이라 Wi-Fi 가 아님: 다시 예약
            else -> SetupService.start(this, SetupService.ACTION_APPLY, silent = true, auto = true)
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
            scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, WifiRetryJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
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
