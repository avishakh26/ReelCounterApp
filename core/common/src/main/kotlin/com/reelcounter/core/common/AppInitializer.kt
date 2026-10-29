package com.reelcounter.core.common

/**
 * Work a module needs started once when the app process starts. Modules contribute with
 * `@Binds @IntoSet`; the Application runs them all in onCreate, so it needn't know each one.
 */
fun interface AppInitializer {
    fun initialize()
}
