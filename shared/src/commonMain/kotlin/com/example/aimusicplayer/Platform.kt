package com.example.aimusicplayer

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform
