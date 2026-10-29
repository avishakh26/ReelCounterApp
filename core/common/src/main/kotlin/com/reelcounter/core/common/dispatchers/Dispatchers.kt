package com.reelcounter.core.common.dispatchers

import javax.inject.Qualifier

/** Inject coroutine dispatchers instead of hard-coding them, so tests can substitute their own. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val dispatcher: RcDispatchers)

enum class RcDispatchers {
    Default,
    IO,
}
