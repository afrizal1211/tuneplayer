package com.tuneplayer.app.ui.util

import androidx.compose.runtime.Composable
import com.tuneplayer.app.R
import com.tuneplayer.app.data.model.Track

internal fun shareTrack(context: android.content.Context, track: Track) {
    val body = if (track.isLocal) {
        "${track.title} — ${track.artist}"
    } else {
        "${track.title} — ${track.artist}\nhttps://music.youtube.com/watch?v=${track.videoId.orEmpty()}"
    }
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, track.title)
        putExtra(android.content.Intent.EXTRA_TEXT, body)
    }
    context.startActivity(
        android.content.Intent.createChooser(send, context.getString(R.string.action_share))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
