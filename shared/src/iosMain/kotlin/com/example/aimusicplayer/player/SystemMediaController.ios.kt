package com.example.aimusicplayer.player

import com.example.aimusicplayer.network.music.SongMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGSize
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatus
import platform.UIKit.UIImage
import platform.darwin.NSObject

/**
 * iOS implementation of [SystemMediaController] using
 * [MPNowPlayingInfoCenter] and [MPRemoteCommandCenter].
 *
 * ## Features
 * - Now Playing info on lock screen and Control Center (title, artist, album, duration, progress)
 * - Cover artwork from [SongMetadata.coverUrl]
 * - Remote commands: play, pause, next, previous, seek, like, repeat
 *
 * No extra dependencies — uses system MediaPlayer framework.
 */
actual class SystemMediaController : SystemMediaControllerProvider {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val infoCenter = MPNowPlayingInfoCenter.defaultCenter
    private val commandCenter = MPRemoteCommandCenter.sharedCommandCenter

    private var player: MusicPlayerProvider? = null
    private var coverArtwork: MPMediaItemArtwork? = null
    private var currentCoverUrl: String? = null
    private var coverJob: Job? = null
    private var isAttached = false

    // ── Callbacks ────────────────────────────────────────────────────

    actual override var onNext: (() -> Unit)? = null
    actual override var onPrevious: (() -> Unit)? = null
    actual override var onToggleRepeatMode: (() -> Unit)? = null
    actual override var onToggleFavorite: (() -> Unit)? = null
    actual override var onDownload: (() -> Unit)? = null  // iOS: not surfaced in lock screen

    // ══════════════════════════════════════════════════════════════
    // Lifecycle
    // ══════════════════════════════════════════════════════════════

    actual override fun attach(player: MusicPlayerProvider) {
        if (isAttached) return  // idempotent
        this.player = player
        setupRemoteCommands()
        observeState(player)
        isAttached = true
    }

    actual override fun detach() {
        disableRemoteCommands()
        infoCenter.nowPlayingInfo = null
        coverArtwork = null
        isAttached = false
        scope.cancel()
        player = null
    }

    // ══════════════════════════════════════════════════════════════
    // State observation
    // ══════════════════════════════════════════════════════════════

    private fun observeState(player: MusicPlayerProvider) {
        scope.launch {
            player.currentSong.collect { song ->
                loadCoverArt(song)         // clears old artwork first, then downloads new one
                updateNowPlayingInfo(song) // reads coverArtwork (now null or just-loaded)
            }
        }
        scope.launch {
            player.isPlaying.collect { playing ->
                updatePlaybackState(playing, player.currentPosition.value, player.duration.value)
            }
        }
        scope.launch {
            player.currentPosition.collect { pos ->
                updatePlaybackState(player.isPlaying.value, pos, player.duration.value)
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Cover art loading (background → main)
    // ══════════════════════════════════════════════════════════════

    private fun loadCoverArt(song: SongMetadata?) {
        val url = song?.coverUrl

        // Cancel any in-flight download from the previous song
        coverJob?.cancel()

        // No cover for this song — clear immediately
        if (url == null) {
            currentCoverUrl = null
            coverArtwork = null
            updateNowPlayingInfo(song)
            return
        }

        // Same cover already loaded or being loaded — skip
        if (url == currentCoverUrl) return

        // New song: clear old artwork immediately so we don't show stale artwork
        currentCoverUrl = url
        coverArtwork = null
        updateNowPlayingInfo(song)

        coverJob = scope.launch(Dispatchers.IO) {
            try {
                val nsUrl = NSURL(string = url)
                val data = NSData(contentsOfURL = nsUrl) ?: return@launch
                val image = UIImage(data = data) ?: return@launch
                val artwork = MPMediaItemArtwork(boundsSize = image.size) { _ -> image }
                withContext(Dispatchers.Main) {
                    // Only apply if this is still the current song's cover
                    if (url == currentCoverUrl) {
                        coverArtwork = artwork
                        // Refresh lock screen with new artwork
                        player?.currentSong?.value?.let { updateNowPlayingInfo(it) }
                    }
                }
            } catch (_: Exception) {
                // Cover not critical — silently skip
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    // Now Playing Info
    // ══════════════════════════════════════════════════════════════

    private fun updateNowPlayingInfo(song: SongMetadata?) {
        if (song == null) {
            infoCenter.nowPlayingInfo = null
            return
        }

        val info = mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to song.songName,
            MPMediaItemPropertyArtist to song.artist,
            MPNowPlayingInfoPropertyElapsedPlaybackTime to (player?.currentPosition?.value ?: 0L) / 1000.0,
            MPMediaItemPropertyPlaybackDuration to song.duration.toDouble(),
            MPNowPlayingInfoPropertyPlaybackRate to if (player?.isPlaying?.value == true) 1.0 else 0.0,
        )
        song.album?.let { info[MPMediaItemPropertyAlbumTitle] = it }
        coverArtwork?.let { info[MPMediaItemPropertyArtwork] = it }

        infoCenter.nowPlayingInfo = info
    }

    private fun updatePlaybackState(playing: Boolean, position: Long, duration: Long) {
        val currentInfo = infoCenter.nowPlayingInfo?.toMutableMap() ?: return
        currentInfo[MPNowPlayingInfoPropertyElapsedPlaybackTime] = position / 1000.0
        currentInfo[MPNowPlayingInfoPropertyPlaybackRate] = if (playing) 1.0 else 0.0
        infoCenter.nowPlayingInfo = currentInfo
    }

    // ══════════════════════════════════════════════════════════════
    // Remote Commands
    // ══════════════════════════════════════════════════════════════

    private fun setupRemoteCommands() {
        commandCenter.playCommand.addTargetWithHandler { _ ->
            player?.resume()
            MPRemoteCommandHandlerStatus.MPRemoteCommandHandlerStatusSuccess
        }
        commandCenter.pauseCommand.addTargetWithHandler { _ ->
            player?.pause()
            MPRemoteCommandHandlerStatus.MPRemoteCommandHandlerStatusSuccess
        }
        commandCenter.togglePlayPauseCommand.addTargetWithHandler { _ ->
            val p = player ?: return@addTargetWithHandler MPRemoteCommandHandlerStatus.MPRemoteCommandHandlerStatusCommandFailed
            if (p.isPlaying.value) p.pause() else p.resume()
            MPRemoteCommandHandlerStatus.MPRemoteCommandHandlerStatusSuccess
        }
        commandCenter.nextTrackCommand.addTargetWithHandler { _ ->
            onNext?.invoke()
            MPRemoteCommandHandlerStatus.MPRemoteCommandHandlerStatusSuccess
        }
        commandCenter.previousTrackCommand.addTargetWithHandler { _ ->
            onPrevious?.invoke()
            MPRemoteCommandHandlerStatus.MPRemoteCommandHandlerStatusSuccess
        }
        commandCenter.changePlaybackPositionCommand.addTargetWithHandler { event ->
            val positionEvent = event as? MPChangePlaybackPositionCommandEvent
                ?: return@addTargetWithHandler MPRemoteCommandHandlerStatus.MPRemoteCommandHandlerStatusCommandFailed
            player?.seekTo((positionEvent.positionTime * 1000).toLong())
            MPRemoteCommandHandlerStatus.MPRemoteCommandHandlerStatusSuccess
        }
        // Like / Favorite
        commandCenter.likeCommand.addTargetWithHandler { _ ->
            onToggleFavorite?.invoke()
            MPRemoteCommandHandlerStatus.MPRemoteCommandHandlerStatusSuccess
        }
        // Repeat mode
        commandCenter.changeRepeatModeCommand.addTargetWithHandler { _ ->
            onToggleRepeatMode?.invoke()
            MPRemoteCommandHandlerStatus.MPRemoteCommandHandlerStatusSuccess
        }
    }

    private fun disableRemoteCommands() {
        commandCenter.playCommand.removeTarget(null)
        commandCenter.pauseCommand.removeTarget(null)
        commandCenter.togglePlayPauseCommand.removeTarget(null)
        commandCenter.nextTrackCommand.removeTarget(null)
        commandCenter.previousTrackCommand.removeTarget(null)
        commandCenter.changePlaybackPositionCommand.removeTarget(null)
        commandCenter.likeCommand.removeTarget(null)
        commandCenter.changeRepeatModeCommand.removeTarget(null)
    }
}
