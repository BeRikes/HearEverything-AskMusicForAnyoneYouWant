package com.example.aimusicplayer.player

/**
 * Platform-agnostic interface for system-level media controls
 * (lock screen, notification shade, Control Center, etc.).
 *
 * An implementation observes [MusicPlayerProvider] state and syncs it
 * to the OS media session, and translates remote-control callbacks
 * back to [MusicPlayerProvider] commands.
 */
interface SystemMediaControllerProvider {
    /**
     * Attach to a [player] and start observing its state.
     * Must be called before playback begins.
     */
    fun attach(player: MusicPlayerProvider)

    /**
     * Stop observing the player and release platform resources
     * (e.g. MediaSession, MPNowPlayingInfoCenter).
     */
    fun detach()

    /** Called by the system when the user taps "next track". */
    var onNext: (() -> Unit)?

    /** Called by the system when the user taps "previous track". */
    var onPrevious: (() -> Unit)?

    /** Called by the system when the user taps "toggle repeat mode". */
    var onToggleRepeatMode: (() -> Unit)?

    /** Called by the system when the user taps "favorite/like". */
    var onToggleFavorite: (() -> Unit)?

    /** Called by the system when the user taps "download" (Android notification only). */
    var onDownload: (() -> Unit)?
}

/**
 * Platform-specific system media controller.
 *
 * - Android: [MediaSession] + [MediaStyle] notification
 * - iOS: [MPNowPlayingInfoCenter] + [MPRemoteCommandCenter]
 */
expect class SystemMediaController : SystemMediaControllerProvider {
    override fun attach(player: MusicPlayerProvider)
    override fun detach()
    override var onNext: (() -> Unit)?
    override var onPrevious: (() -> Unit)?
    override var onToggleRepeatMode: (() -> Unit)?
    override var onToggleFavorite: (() -> Unit)?
    override var onDownload: (() -> Unit)?
}
