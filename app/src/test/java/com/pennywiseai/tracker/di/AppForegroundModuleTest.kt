package com.pennywiseai.tracker.di

import android.app.Application
import android.content.Context
import com.pennywiseai.tracker.PennyWiseApplication
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** AppForegroundState reads PennyWiseApplication's flag on every call, as ConscienceManager's cast did. */
class AppForegroundModuleTest {

    @Test
    fun `reads the application's foreground flag at call time`() {
        val app = mockk<PennyWiseApplication> { every { isAppInForeground } returnsMany listOf(true, false) }
        val context = mockk<Context> { every { applicationContext } returns app }

        val state = AppForegroundModule.provideAppForegroundState(context)

        assertTrue(state.isInForeground())
        assertFalse(state.isInForeground())
    }

    @Test
    fun `is false under another Application`() {
        val context = mockk<Context> { every { applicationContext } returns mockk<Application>() }

        assertFalse(AppForegroundModule.provideAppForegroundState(context).isInForeground())
    }
}
