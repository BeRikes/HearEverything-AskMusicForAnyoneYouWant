package com.example.aimusicplayer.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Lightweight ViewModel base class for KMP.
 *
 * Manages a [CoroutineScope] with [SupervisorJob] so child coroutines
 * fail independently.  Call [onCleared] when the ViewModel is no longer
 * needed (e.g. when the screen is popped).
 *
 * ## Usage
 * ```kotlin
 * class MyViewModel : ViewModel() {
 *     val state = MutableStateFlow(0)
 *     fun increment() { state.value++ }
 * }
 * ```
 *
 * No external dependency required — only kotlinx-coroutines (already in the project).
 */
abstract class ViewModel {

    /** Coroutine scope tied to this ViewModel's lifecycle. */
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Cancel all running coroutines and release resources.
     * Must be called when the ViewModel is disposed.
     */
    open fun onCleared() {
        scope.cancel()
    }
}
