package com.tuneplayer.app.data.model

/**
 * Technical details of the audio currently being decoded.
 *
 * Values are best-effort: containers do not always expose a bitrate, and
 * streams may only report it after the player has loaded the track. The UI
 * hides whatever is missing rather than showing zeros.
 */
data class AudioInfo(
    val codec: String? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val channelCount: Int? = null,
) {
    companion object {
        /** Friendly codec name from a MIME type or Media3's codec string. */
        fun codecLabel(mimeType: String?, codecs: String? = null): String? {
            val mime = mimeType?.lowercase().orEmpty()
            val codec = codecs?.lowercase().orEmpty()
            return when {
                mime.contains("opus") || codec.contains("opus") -> "Opus"
                mime.contains("mp4a") || mime.contains("audio/mp4") || codec.startsWith("mp4a") -> "AAC"
                mime.contains("mpeg") || mime.contains("audio/mp3") -> "MP3"
                mime.contains("flac") || codec.contains("flac") -> "FLAC"
                mime.contains("vorbis") || codec.contains("vorbis") -> "Vorbis"
                mime.contains("pcm") || mime.contains("audio/wav") -> "PCM"
                mime.contains("ac-3") || mime.contains("eac3") -> "Dolby"
                else -> codecs?.substringBefore('.')?.uppercase()?.takeIf { it.isNotBlank() }
            }
        }
    }
}
