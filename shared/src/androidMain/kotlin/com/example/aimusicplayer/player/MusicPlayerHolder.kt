package com.example.aimusicplayer.player

object MusicPlayerHolder {
    lateinit var musicPlayer: MusicPlayer
    lateinit var systemMediaController: SystemMediaController

    fun isInitialized(): Boolean = ::musicPlayer.isInitialized
}
