package com.ryanjames.lunar

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform