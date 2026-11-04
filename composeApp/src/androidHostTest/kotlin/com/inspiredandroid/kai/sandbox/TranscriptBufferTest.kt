package com.inspiredandroid.kai.sandbox

import androidx.compose.runtime.snapshots.Snapshot
import com.inspiredandroid.kai.TerminalLine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TranscriptBufferTest {

    @Test
    fun `transcript created inside a discarded snapshot stays readable`() {
        val snapshot = Snapshot.takeMutableSnapshot()
        val buffer = try {
            snapshot.enter { TranscriptBuffer(listOf(TerminalLine.Output("restored")), maxLines = 10) }
        } finally {
            snapshot.dispose()
        }
        assertEquals(listOf<TerminalLine>(TerminalLine.Output("restored")), buffer.transcript.toList())
    }

    @Test
    fun `background appends are committed in one batch on the ui dispatcher`() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val buffer = TranscriptBuffer(emptyList(), maxLines = 1_000, uiDispatcher = dispatcher)

        thread { repeat(100) { buffer.append(TerminalLine.Output("line $it")) } }.join()
        assertTrue(buffer.transcript.isEmpty())

        scope.advanceUntilIdle()
        assertEquals(100, buffer.transcript.size)
        assertEquals(TerminalLine.Output("line 99"), buffer.transcript.last())
    }

    @Test
    fun `drain keeps only the newest lines`() = runTest {
        val buffer = TranscriptBuffer(
            listOf(TerminalLine.Command("old")),
            maxLines = 3,
            uiDispatcher = StandardTestDispatcher(testScheduler),
        )
        repeat(5) { buffer.append(TerminalLine.Output("$it")) }
        val lines = buffer.flush()
        assertEquals(listOf("2", "3", "4"), lines.map { it.text })
        assertEquals(lines, buffer.transcript.toList())
    }

    @Test
    fun `paused buffer skips trim until resumed`() = runTest {
        val buffer = TranscriptBuffer(emptyList(), maxLines = 2, uiDispatcher = StandardTestDispatcher(testScheduler))
        buffer.setPrunePaused(true)
        repeat(4) { buffer.append(TerminalLine.Output("$it")) }
        assertEquals(4, buffer.flush().size)
        buffer.setPrunePaused(false)
        assertEquals(listOf("2", "3"), buffer.transcript.map { it.text })
    }
}
