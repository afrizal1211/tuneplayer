package com.tuneplayer.app.ui.util

import android.content.Context
import android.content.Intent
import com.tuneplayer.app.R
import com.tuneplayer.app.data.model.Track

internal fun shareTrack(context: Context, track: Track) {
    val body = if (track.isLocal) {
        "${track.title} — ${track.artist}"
    } else {
        "${track.title} — ${track.artist}\nhttps://music.youtube.com/watch?v=${track.videoId.orEmpty()}"
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, track.title)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    context.startActivity(
        Intent.createChooser(send, context.getString(R.string.action_share))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}
