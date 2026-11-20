package com.pennywiseai.tracker.ui.text

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

private val LocalTextFieldCommits = staticCompositionLocalOf<MutableMap<View, MutableSet<() -> Unit>>?> { null }

@Composable
fun TextFieldCommitScope(content: @Composable () -> Unit) {
    val commits = remember { mutableMapOf<View, MutableSet<() -> Unit>>() }
    CompositionLocalProvider(LocalTextFieldCommits provides commits, content = content)
}

@Composable
fun RegisterTextFieldCommit(commit: () -> Unit) {
    val commits = LocalTextFieldCommits.current ?: return
    val view = LocalView.current
    val latestCommit by rememberUpdatedState(commit)
    DisposableEffect(commits, view) {
        val callbacks = commits.getOrPut(view) { mutableSetOf() }
        val callback = { latestCommit() }
        callbacks.add(callback)
        onDispose {
            callbacks.remove(callback)
            if (callbacks.isEmpty()) commits.remove(view)
        }
    }
}

/** Submit using the current text, even if snapshotFlow hasn't delivered the last edit yet. */
@Composable
fun withCommittedText(onSubmit: () -> Unit): () -> Unit {
    val commits = LocalTextFieldCommits.current
    val view = LocalView.current
    return {
        val window = view.rootView
        commits?.filterKeys { it.rootView === window }?.values?.flatten()?.forEach { it() }
        onSubmit()
    }
}
