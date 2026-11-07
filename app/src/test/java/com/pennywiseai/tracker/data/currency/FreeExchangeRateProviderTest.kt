package com.pennywiseai.tracker.data.currency

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Test

class FreeExchangeRateProviderTest {

    /**
     * A widget worker replaced mid-fetch used to get null back, which reads as
     * "the API failed" and blocks every fetch for the 5-minute cooldown. Cancelled
     * before the request starts, so this never touches the network.
     */
    @Test
    fun `a cancelled fetch stays cancelled instead of looking like a failed one`() = runBlocking {
        var returned = false
        launch(Dispatchers.Default) {
            cancel()
            FreeExchangeRateProvider().fetchAllExchangeRatesWithMetadata("USD")
            returned = true
        }.join()
        assertFalse(returned)
    }
}
