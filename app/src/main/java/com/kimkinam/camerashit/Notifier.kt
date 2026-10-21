package com.kimkinam.camerashit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri

/** 서비스 밖(부팅 리시버 등)에서 쓰는 단순 알림 도우미. */
object Notifier {
    const val GITHUB_URL = "https://github.com/Kim-KiNam/CameraShit"

    const val ID_NOTICE = 3
    const val ID_GITHUB = 4

    private const val CHANNEL = "notice"

    fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun openGithub(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 2, Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)), PendingIntent.FLAG_IMMUTABLE
        )

    private fun post(context: Context, id: Int, title: String, text: String, target: PendingIntent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.notice_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(target)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }

    /** 앱을 여는 안내 알림. */
    fun notice(context: Context, text: String) =
        post(context, ID_NOTICE, context.getString(R.string.app_name), text, openApp(context))

    /** 별도 알림: 문제가 계속되면 확인할 GitHub 주소(누르면 브라우저로 열림). */
    fun github(context: Context) =
        post(
            context, ID_GITHUB, context.getString(R.string.github_title),
            context.getString(R.string.github_text, GITHUB_URL), openGithub(context)
        )
}
