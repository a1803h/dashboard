package tj.umed.drivedashboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Only used to receive MediaSession access; V1 never displays notification content. */
class MediaAccessService : NotificationListenerService()

data class MediaViewState(
    val title: String = "Song Title",
    val artist: String = "Artist Name",
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val playing: Boolean = false,
    val artwork: Bitmap? = null,
    val available: Boolean = false,
    val canShuffle: Boolean = false,
    val canRepeat: Boolean = false,
    val packageName: String = ""
)

class MediaBridge(private val context: Context) {
    private val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private var controller: MediaController? = null
    private var compat: MediaControllerCompat? = null
    var state by mutableStateOf(MediaViewState()); private set
    var hasNotificationAccess by mutableStateOf(false); private set

    fun refresh(preferredPackage: String) {
        val component = ComponentName(context, MediaAccessService::class.java)
        val sessions = try {
            val result = manager.getActiveSessions(component)
            hasNotificationAccess = true
            result
        } catch (_: SecurityException) {
            hasNotificationAccess = false
            emptyList()
        }
        val selected = when {
            preferredPackage.isNotBlank() -> sessions.firstOrNull { it.packageName == preferredPackage }
            else -> sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: sessions.firstOrNull()
        }
        controller = selected
        compat = runCatching { selected?.let {
            MediaControllerCompat(context, MediaSessionCompat.Token.fromToken(it.sessionToken))
        }}.getOrNull()
        if (selected == null) { state = MediaViewState(); return }
        val meta = selected.metadata
        val play = selected.playbackState
        val playbackPosition = if (play == null) 0L else {
            val dt = if (play.state == PlaybackState.STATE_PLAYING)
                SystemClock.elapsedRealtime() - play.lastPositionUpdateTime else 0L
            (play.position + (dt.coerceAtLeast(0) * play.playbackSpeed).toLong()).coerceAtLeast(0L)
        }
        val duration = meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val mediaActions = compat?.playbackState?.actions ?: 0L
        state = MediaViewState(
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "Song Title",
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: "Artist Name",
            durationMs = duration,
            positionMs = if (duration > 0) playbackPosition.coerceAtMost(duration) else playbackPosition,
            playing = play?.state == PlaybackState.STATE_PLAYING,
            artwork = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON),
            available = true,
            canShuffle = (mediaActions and PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE) != 0L,
            canRepeat = (mediaActions and PlaybackStateCompat.ACTION_SET_REPEAT_MODE) != 0L,
            packageName = selected.packageName
        )
    }

    fun playPause() { controller?.let { if (state.playing) it.transportControls.pause() else it.transportControls.play() } }
    fun skipNext() { controller?.transportControls?.skipToNext() }
    fun skipPrev() { controller?.transportControls?.skipToPrevious() }
    fun seek(progress: Float) {
        val duration = state.durationMs
        if (duration > 0) controller?.transportControls?.seekTo((progress.coerceIn(0f,1f) * duration).toLong())
    }
    fun shuffle() {
        val c = compat ?: return
        if (!state.canShuffle) return
        c.transportControls.setShuffleMode(if (c.shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_NONE)
            PlaybackStateCompat.SHUFFLE_MODE_ALL else PlaybackStateCompat.SHUFFLE_MODE_NONE)
    }
    fun repeat() {
        val c = compat ?: return
        if (!state.canRepeat) return
        c.transportControls.setRepeatMode(if (c.repeatMode == PlaybackStateCompat.REPEAT_MODE_NONE)
            PlaybackStateCompat.REPEAT_MODE_ALL else PlaybackStateCompat.REPEAT_MODE_NONE)
    }
    fun notificationPermissionIntent() = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
}
