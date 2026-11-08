package com.inspiredandroid.kai.sandbox

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.inspiredandroid.kai.TerminalLine
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bounded, UI-observed terminal transcript that can be appended to from any
 * thread.
 *
 * Lines from the proot stdio readers are queued and committed in batches on
 * [uiDispatcher]. Committing a snapshot per line from background threads ran
 * Compose's apply observers off the main thread on every line, contending for
 * the same locks the main thread's LazyColumn measure needs (ANRs, and a
 * blocked main thread delaying DaemonService callbacks).
 */
internal class TranscriptBuffer(
    initialLines: List<TerminalLine>,
    private val maxLines: Int,
    private val uiDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    // Created in the global snapshot: the owning shell is created lazily and the
    // first caller can be a composition. A state object created inside a
    // composition snapshot that is never applied stays unreadable forever
    // ("Reading a state that was created after the snapshot was taken").
    val transcript: SnapshotStateList<TerminalLine> = Snapshot.global {
        mutableStateListOf<TerminalLine>().apply { addAll(initialLines) }
    }

    private val scope = CoroutineScope(SupervisorJob() + uiDispatcher)
    private val pending = ConcurrentLinkedQueue<TerminalLine>()
    private val drainScheduled = AtomicBoolean(false)

    @Volatile
    private var prunePaused: Boolean = false

    /** Queue [line]; it becomes visible in [transcript] on the next drain on [uiDispatcher]. */
    fun append(line: TerminalLine) {
        pending.add(line)
        if (drainScheduled.compareAndSet(false, true)) {
            scope.launch { drain() }
        }
    }

    /** Commit everything queued so far and return the resulting transcript. */
    suspend fun flush(): List<TerminalLine> = withContext(uiDispatcher) {
        drain()
        transcript.toList()
    }

    /** See [SessionShell.setPrunePaused]. Must be called on the UI thread. */
    fun setPrunePaused(value: Boolean) {
        val wasPaused = prunePaused
        prunePaused = value
        if (wasPaused && !value) {
            Snapshot.withMutableSnapshot { trim() }
        }
    }

    private fun drain() {
        // Reset before polling so a line queued mid-drain schedules another pass.
        drainScheduled.set(false)
        if (pending.isEmpty()) return
        val batch = ArrayList<TerminalLine>()
        while (true) batch.add(pending.poll() ?: break)
        // Add+trim must commit as a single snapshot. Otherwise a LazyColumn
        // measure pass can capture size=N+1 then read index N after the trim
        // shrunk the list — IndexOutOfBoundsException under heavy bursts.
        Snapshot.withMutableSnapshot {
            transcript.addAll(if (prunePaused) batch else batch.takeLast(maxLines))
            if (!prunePaused) trim()
        }
    }

    private fun trim() {
        val excess = transcript.size - maxLines
        if (excess > 0) transcript.subList(0, excess).clear()
    }
}
