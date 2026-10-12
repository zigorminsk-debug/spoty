package io.github.zigorminsk.spoty.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.zigorminsk.spoty.R

object NotificationChannels {
    const val PLAYBACK = "playback"
    const val DOWNLOADS = "downloads"
    const val RELEASES = "releases"

    fun ensure(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(PLAYBACK, context.getString(R.string.channel_playback), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(DOWNLOADS, context.getString(R.string.channel_downloads), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(RELEASES, context.getString(R.string.channel_releases), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}
