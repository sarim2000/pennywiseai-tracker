import com.example.ui.ProViews
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProOfferTest {
    @Test
    fun `the offer runs until 1159 pm IST on 10 Nov`() {
        assertTrue(ProViews.offerActive(Instant.parse("2026-11-10T18:29:59Z")))
        assertFalse(ProViews.offerActive(Instant.parse("2026-11-10T18:30:00Z")))
    }
}
