package com.pennywiseai.tracker.core

import javax.inject.Qualifier

/**
 * Qualifier for an application-lifetime [kotlinx.coroutines.CoroutineScope] — use this for
 * fire-and-forget work that must outlive the calling Activity/ViewModel
 * (e.g. a DB write started from a transient activity that finishes
 * immediately after).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
