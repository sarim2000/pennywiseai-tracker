package com.pennywiseai.tracker.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class NavDockStateTest {
    private fun action() = NavDockAction(Icons.Filled.Add, "", onClick = {})

    @Test
    fun `fast return to a tab still composed keeps its action once the other tab leaves`() {
        val dock = NavDockState()
        val home = Any(); val txns = Any()
        val sync = action(); val add = action(); val syncing = action()
        dock.claim(home, sync)
        dock.claim(txns, add) // Home -> Transactions: Home is still fading out
        dock.set(home, syncing) // back to Home mid-fade: no new claim, only recompositions
        assertSame(add, dock.action) // the outgoing tab can't overwrite the top
        dock.clear(txns) // Transactions finishes leaving
        assertSame(syncing, dock.action) // Home's latest action, not an empty dock
        dock.clear(home)
        assertNull(dock.action)
    }
}
