package com.pennywiseai.tracker.core

import javax.inject.Qualifier

/**
 * Qualifier for the [kotlinx.coroutines.CoroutineDispatcher] that CPU-bound work runs on
 * (`Dispatchers.Default` in the app). Injected so a test can run that work on its own
 * test dispatcher, in virtual time, on one thread.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
