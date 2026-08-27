package com.pennywiseai.tracker.data.repository

import android.content.Context
import android.content.ContextWrapper
import com.pennywiseai.tracker.data.currency.CurrencyConversionService
import com.pennywiseai.tracker.data.currency.ExchangeRateProvider
import com.pennywiseai.tracker.data.database.dao.ChatDao
import com.pennywiseai.tracker.data.database.dao.ExchangeRateDao
import com.pennywiseai.tracker.data.database.dao.SubscriptionDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.ChatMessage
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.domain.service.LlmService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy

/**
 * Guards the contract that a base-currency change **re-renders** the AI system prompt
 * rather than destroying the conversation.
 *
 * Regression cover for two defects found in review:
 *  - clearing only the DataStore prompt left an existing chat answering in the old
 *    currency, because the session is rebuilt from the Room copy;
 *  - the first fix cleared the whole chat, so re-picking the currency already in use
 *    silently deleted the user's history.
 */
class LlmRepositoryPromptRefreshTest {

    /**
     * Context stub that never touches disk. `preferencesDataStore` caches one DataStore
     * per process keyed by file name, so any test that drives the real thing fights
     * every other test in the JVM for the same instance — hence the stub prefs below.
     */
    class FakeContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    /**
     * Base currency held in memory, so the prompt renders in NGN without DataStore.
     * Only the two members [LlmRepository.refreshSystemPrompt] actually reads are
     * overridden; everything else stays inherited.
     */
    class StubPrefs(
        private var currency: String
    ) : UserPreferencesRepository(FakeContext()) {
        var storedPrompt: String? = null
        override val baseCurrency: Flow<String> get() = flowOf(currency)
        override suspend fun updateSystemPrompt(prompt: String) { storedPrompt = prompt }
    }

    /** Records what the LLM layer was asked to rebuild. */
    class FakeLlmService : LlmService {
        var conversationOpen = false
        var lastSystemPrompt: String? = null
        var lastHistory: List<Pair<String, Boolean>> = emptyList()
        var createCount = 0

        override suspend fun initialize(modelPath: String): Result<Unit> = Result.success(Unit)
        override suspend fun createConversation(
            systemPrompt: String,
            history: List<Pair<String, Boolean>>
        ): Result<Unit> {
            lastSystemPrompt = systemPrompt
            lastHistory = history
            conversationOpen = true
            createCount++
            return Result.success(Unit)
        }
        override fun sendMessage(message: String): Flow<String> = flowOf("")
        override fun hasActiveConversation(): Boolean = conversationOpen
        override suspend fun closeConversation() { conversationOpen = false }
        override suspend fun reset() { conversationOpen = false }
        override fun isInitialized(): Boolean = true
    }

    private lateinit var messages: MutableList<ChatMessage>
    private lateinit var chatDao: ChatDao
    private lateinit var llmService: FakeLlmService
    private lateinit var prefs: StubPrefs
    private lateinit var repository: LlmRepository

    @Before
    fun setUp() = runBlocking {
        messages = mutableListOf()
        llmService = FakeLlmService()

        chatDao = Proxy.newProxyInstance(
            ChatDao::class.java.classLoader,
            arrayOf(ChatDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getAllMessagesForContext" -> messages.sortedBy { it.timestamp }
                "insertMessage" -> { messages.add(args[0] as ChatMessage); Unit }
                "deleteSystemPromptMessages" -> { messages.removeAll { it.isSystemPrompt }; Unit }
                "deleteAllMessages" -> { messages.clear(); Unit }
                "getMessageCount" -> messages.size
                else -> null
            }
        } as ChatDao

        prefs = StubPrefs("NGN")

        val fakeTransactionDao = Proxy.newProxyInstance(
            TransactionDao::class.java.classLoader,
            arrayOf(TransactionDao::class.java)
        ) { _, method, _ ->
            if (method.name == "getTransactionsBetweenDatesList") emptyList<Any>() else null
        } as TransactionDao

        val fakeSubscriptionDao = Proxy.newProxyInstance(
            SubscriptionDao::class.java.classLoader,
            arrayOf(SubscriptionDao::class.java)
        ) { _, method, _ ->
            if (method.name == "getSubscriptionsByStateList") emptyList<Any>() else null
        } as SubscriptionDao

        val fakeExchangeRateDao = Proxy.newProxyInstance(
            ExchangeRateDao::class.java.classLoader,
            arrayOf(ExchangeRateDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "hasValidRate" -> 0
                else -> null
            }
        } as ExchangeRateDao

        val fakeRateProvider = Proxy.newProxyInstance(
            ExchangeRateProvider::class.java.classLoader,
            arrayOf(ExchangeRateProvider::class.java)
        ) { _, _, _ -> null } as ExchangeRateProvider

        val aiContextRepository = AiContextRepository(
            transactionDao = fakeTransactionDao,
            subscriptionDao = fakeSubscriptionDao,
            userPreferencesRepository = prefs,
            currencyConversionService = CurrencyConversionService(
                exchangeRateDao = fakeExchangeRateDao,
                exchangeRateProvider = fakeRateProvider,
                userPreferencesRepository = prefs
            )
        )

        repository = LlmRepository(
            llmService = llmService,
            chatDao = chatDao,
            modelRepository = ModelRepository(FakeContext()),
            aiContextRepository = aiContextRepository,
            userPreferencesRepository = prefs
        )
    }

    private fun seedChat(promptCurrencyMarker: String) {
        messages.add(
            ChatMessage(
                message = "You are PennyWise AI. This month: " + promptCurrencyMarker + " spent",
                isUser = false,
                timestamp = 1_000L,
                isSystemPrompt = true
            )
        )
        messages.add(ChatMessage(message = "how much did I spend?", isUser = true, timestamp = 2_000L))
        messages.add(ChatMessage(message = "You spent " + promptCurrencyMarker, isUser = false, timestamp = 3_000L))
    }

    @Test
    fun `refresh preserves the visible conversation`() = runBlocking {
        seedChat("STALE500")
        repository.refreshSystemPrompt()

        val visible = messages.filter { !it.isSystemPrompt }.sortedBy { it.timestamp }
        assertEquals(
            "user messages must survive a currency change",
            listOf("how much did I spend?", "You spent STALE500"),
            visible.map { it.message }
        )
    }

    @Test
    fun `refresh replaces the hidden system prompt with the new currency`() = runBlocking {
        seedChat("STALE500")
        repository.refreshSystemPrompt()

        val systemRows = messages.filter { it.isSystemPrompt }
        assertEquals("exactly one system prompt row", 1, systemRows.size)
        assertFalse(
            "the stale prompt must be gone",
            systemRows.first().message.contains("STALE500")
        )
        assertTrue(
            "rebuilt prompt should render the new currency",
            systemRows.first().message.contains("\u20A6")
        )
    }

    @Test
    fun `refresh keeps the system prompt ordered ahead of the conversation`() = runBlocking {
        seedChat("STALE500")
        repository.refreshSystemPrompt()

        val ordered = messages.sortedBy { it.timestamp }
        assertTrue(
            "system prompt must remain first so context replay stays correct",
            ordered.first().isSystemPrompt
        )
    }

    @Test
    fun `refresh rebuilds the live conversation with the same history`() = runBlocking {
        seedChat("STALE500")
        repository.refreshSystemPrompt()

        assertTrue("conversation should be live again", llmService.conversationOpen)
        assertEquals(
            "history must be replayed so the model keeps context",
            listOf("how much did I spend?", "You spent STALE500"),
            llmService.lastHistory.map { it.first }
        )
        assertTrue(
            "the rebuilt conversation must carry the new-currency prompt",
            llmService.lastSystemPrompt?.contains("\u20A6") == true
        )
    }

    @Test
    fun `refresh on an empty chat does not create a conversation`() = runBlocking {
        repository.refreshSystemPrompt()

        assertTrue("no messages should be invented", messages.isEmpty())
        assertEquals("no conversation should be built for an empty chat", 0, llmService.createCount)
    }
}
