package com.inspiredandroid.kai.ui.chat

import com.inspiredandroid.kai.data.TaskScheduler
import com.inspiredandroid.kai.skills.SkillManifest
import com.inspiredandroid.kai.testutil.FakeDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelSkillTest {

    private val testDispatcher = StandardTestDispatcher()
    private val unconfinedDispatcher = UnconfinedTestDispatcher()
    private lateinit var fakeRepository: FakeDataRepository

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeDataRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ChatViewModel {
        val noOpScheduler = TaskScheduler(fakeRepository, enabled = false)
        return ChatViewModel(fakeRepository, noOpScheduler, unconfinedDispatcher)
    }

    private fun skill(id: String) = SkillManifest(
        id = id,
        displayName = id,
        description = "desc",
        body = "body",
    )

    @Test
    fun `leading slash command routes the matching skill id and keeps text verbatim`() = runTest {
        fakeRepository.skills = listOf(skill("summarize"))
        val viewModel = createViewModel()

        viewModel.state.value.actions.ask("/summarize this article please")
        advanceUntilIdle()

        assertEquals("summarize", fakeRepository.lastActiveSkillId)
        assertEquals("/summarize this article please", fakeRepository.askCalls.last().first)
    }

    @Test
    fun `unmatched slash command passes no skill id`() = runTest {
        fakeRepository.skills = listOf(skill("summarize"))
        val viewModel = createViewModel()

        viewModel.state.value.actions.ask("/unknown do something")
        advanceUntilIdle()

        assertNull(fakeRepository.lastActiveSkillId)
    }

    @Test
    fun `plain message passes no skill id`() = runTest {
        fakeRepository.skills = listOf(skill("summarize"))
        val viewModel = createViewModel()

        viewModel.state.value.actions.ask("just a normal question")
        advanceUntilIdle()

        assertNull(fakeRepository.lastActiveSkillId)
    }
}
