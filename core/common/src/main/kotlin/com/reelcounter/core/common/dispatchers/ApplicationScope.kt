package com.reelcounter.core.common.dispatchers

import javax.inject.Qualifier

/**
 * A CoroutineScope that lives as long as the app process, for work that must outlive a single
 * screen, such as keeping the signed-in user's profile in sync.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
