package com.tuneplayer.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.tuneplayer.app.MainActivity
import com.tuneplayer.app.R

/**
 * Single-button widget: shows the current track and opens the player.
 *
 * State is mirrored into prefs by the ViewModel because a widget cannot bind to
 * a MediaController cheaply. Register new fields in [NOW_PLAYING_PREFS].
 */
class NowPlayingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context)) }
    }

    companion object {
        const val NOW_PLAYING_PREFS = "tuneplayer_now_playing"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_IS_PLAYING = "is_playing"

        /** Called by the ViewModel whenever a track or playback state changes. */
        fun publish(context: Context, title: String?, artist: String?, isPlaying: Boolean) {
            context.getSharedPreferences(NOW_PLAYING_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TITLE, title)
                .putString(KEY_ARTIST, artist)
                .putBoolean(KEY_IS_PLAYING, isPlaying)
                .apply()
            refresh(context)
        }

        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val component = ComponentName(context, NowPlayingWidget::class.java)
            manager.getAppWidgetIds(component).forEach { id ->
                manager.updateAppWidget(id, buildViews(context))
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val prefs = context.getSharedPreferences(NOW_PLAYING_PREFS, Context.MODE_PRIVATE)
            val title = prefs.getString(KEY_TITLE, null) ?: "Nothing playing"
            val artist = prefs.getString(KEY_ARTIST, null).orEmpty()
            val isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false)

            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            return RemoteViews(context.packageName, R.layout.widget_now_playing).apply {
                setTextViewText(R.id.widget_title, title)
                setTextViewText(R.id.widget_subtitle, artist)
                setImageViewResource(
                    R.id.widget_play,
                    if (isPlaying) {
                        android.R.drawable.ic_media_pause
                    } else {
                        android.R.drawable.ic_media_play
                    },
                )
                setOnClickPendingIntent(R.id.widget_root, open)
            }
        }
    }
}
