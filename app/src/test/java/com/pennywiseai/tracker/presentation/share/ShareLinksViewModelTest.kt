package com.pennywiseai.tracker.presentation.share

import android.graphics.Bitmap
import com.pennywiseai.tracker.data.share.CreatedShare
import com.pennywiseai.tracker.data.share.ShareLinkClient
import com.pennywiseai.tracker.data.share.ShareLinkError
import com.pennywiseai.tracker.data.share.ShareLinkExpiry
import com.pennywiseai.tracker.data.share.ShareLinkStore
import com.pennywiseai.tracker.data.share.SharedLink
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.Locale

/** A phone that can't write its list of links says so instead of crashing, and leaves no page it can't take down. */
class ShareLinksViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val created = CreatedShare(id = "abc", url = "https://example.test/s/abc", deleteToken = "token")
    private val client = mockk<ShareLinkClient>()
    private val store = mockk<ShareLinkStore> { every { links } returns emptyFlow() }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic("com.pennywiseai.tracker.presentation.share.ShareLinksViewModelKt")
        every { any<Bitmap>().toOgPng() } returns ByteArray(0)
    }

    @After
    fun tearDown() {
        unmockkStatic("com.pennywiseai.tracker.presentation.share.ShareLinksViewModelKt")
        Dispatchers.resetMain()
    }

    private fun ShareLinksViewModel.publishSample() =
        publish(previewSpending, ShareTheme.AURORA, Locale.US, ShareLinkExpiry.DEFAULT, mockk())

    /** The image is encoded off the main thread, so wait for the outcome rather than for idle. */
    private suspend fun ShareLinksViewModel.settledPublish() = publish.first { it != PublishState.Publishing }

    @Test
    fun `a link whose delete key can't be saved is taken down again`() = runTest(dispatcher) {
        coEvery { client.publish(any(), any()) } returns created
        coEvery { client.delete(any(), any()) } returns Unit
        coEvery { store.add(any(), any()) } throws IOException("disk full")
        val viewModel = ShareLinksViewModel(client, store)

        viewModel.publishSample()

        assertEquals(PublishState.Failed(ShareLinkError.Storage), viewModel.settledPublish())
        coVerify(exactly = 1) { client.delete("abc", "token") }
    }

    @Test
    fun `a failed take-down still reports the storage failure`() = runTest(dispatcher) {
        coEvery { client.publish(any(), any()) } returns created
        coEvery { client.delete(any(), any()) } throws ShareLinkError.Network
        coEvery { store.add(any(), any()) } throws IOException("disk full")
        val viewModel = ShareLinksViewModel(client, store)

        viewModel.publishSample()

        assertEquals(PublishState.Failed(ShareLinkError.Storage), viewModel.settledPublish())
    }

    @Test
    fun `a page that couldn't be taken down is taken down before the next one is made`() = runTest(dispatcher) {
        coEvery { client.publish(any(), any()) } returns created
        coEvery { client.delete(any(), any()) } throws ShareLinkError.Network
        coEvery { store.add(any(), any()) } throws IOException("disk full")
        val viewModel = ShareLinksViewModel(client, store)
        viewModel.publishSample()
        viewModel.settledPublish()
        viewModel.publishHandled()

        // Still offline: the retry fails on the old page and makes no new one.
        viewModel.publishSample()
        assertEquals(PublishState.Failed(ShareLinkError.Network), viewModel.settledPublish())
        coVerify(exactly = 1) { client.publish(any(), any()) }
        viewModel.publishHandled()

        // Back online, with room to save: the old page goes, the new one is kept.
        coEvery { client.delete(any(), any()) } returns Unit
        coEvery { store.add(any(), any()) } returns Unit
        viewModel.publishSample()
        assertEquals(true, viewModel.settledPublish() is PublishState.Published)
        coVerify(exactly = 3) { client.delete("abc", "token") }
        coVerify(exactly = 2) { client.publish(any(), any()) }
    }

    @Test
    fun `a delete the phone can't record is reported, not thrown`() = runTest(dispatcher) {
        coEvery { client.delete(any(), any()) } returns Unit
        coEvery { store.remove(any()) } throws IOException("disk full")
        val viewModel = ShareLinksViewModel(client, store)
        val link = SharedLink(id = "abc", url = created.url, deleteToken = "token")

        viewModel.delete(link)
        advanceUntilIdle()

        assertEquals(DeleteOutcome.Failed(ShareLinkError.Storage), viewModel.deleteOutcomes.first())
        assertEquals(emptySet<String>(), viewModel.deleting.value)
    }
}
