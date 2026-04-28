package com.inspiredandroid.kai.ui.sandbox

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inspiredandroid.kai.CommandHandle
import com.inspiredandroid.kai.SandboxController
import com.inspiredandroid.kai.ui.settings.TerminalLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private const val MAX_OUTPUT_LINES = 500
private const val STREAM_BUFFER_CAPACITY = 256
private const val STREAM_FLUSH_INTERVAL_MS = 32L
private const val STREAM_FLUSH_BATCH_MAX = 200

class SandboxSessionViewModel(
    private val sandboxController: SandboxController,
) : ViewModel() {

    private val selectedTabState = MutableStateFlow(SandboxSubTab.Terminal)
    internal val selectedTab = selectedTabState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText = _inputText.asStateFlow()

    val outputLines: SnapshotStateList<TerminalLine> = mutableStateListOf()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _activeHandle = MutableStateFlow<CommandHandle?>(null)
    val activeHandle = _activeHandle.asStateFlow()

    internal fun selectTab(tab: SandboxSubTab) {
        selectedTabState.value = tab
    }

    fun setInputText(text: String) {
        _inputText.value = text
    }

    fun submit() {
        val line = _inputText.value
        if (line.isBlank()) return
        _inputText.value = ""
        val handle = _activeHandle.value
        if (_isRunning.value && handle != null) {
            outputLines.add(TerminalLine.Output(line))
            viewModelScope.launch { handle.writeInput(line) }
        } else if (!_isRunning.value) {
            viewModelScope.launch { runCommand(line.trim()) }
        }
    }

    fun cancelRunning() {
        _activeHandle.value?.cancel()
    }

    private suspend fun runCommand(command: String) {
        if (command == "clear") {
            outputLines.clear()
            return
        }
        outputLines.add(TerminalLine.Command(command))
        _isRunning.value = true

        val channel = Channel<TerminalLine>(
            capacity = STREAM_BUFFER_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

        var handle: CommandHandle? = null
        try {
            coroutineScope {
                val drainJob = launch { drainStreamedLines(channel, outputLines) }
                val h = sandboxController.executeCommandStreaming(
                    command = command,
                    onStdout = { line -> channel.trySend(TerminalLine.Output(line)) },
                    onStderr = { line -> channel.trySend(TerminalLine.Error(line)) },
                )
                handle = h
                _activeHandle.value = h
                try {
                    h.awaitExit()
                } finally {
                    channel.close()
                    drainJob.join()
                }
            }
            if (handle?.isCancelled() == true) {
                outputLines.add(TerminalLine.Output("^C"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            outputLines.add(TerminalLine.Error(e.message ?: "Command failed"))
        } finally {
            _activeHandle.value = null
            pruneOutput(outputLines)
            _isRunning.value = false
        }
    }

    override fun onCleared() {
        _activeHandle.value?.cancel()
        super.onCleared()
    }
}

private suspend fun drainStreamedLines(
    channel: Channel<TerminalLine>,
    outputLines: MutableList<TerminalLine>,
) {
    while (true) {
        val batch = ArrayList<TerminalLine>(STREAM_FLUSH_BATCH_MAX)
        var closed = false
        while (batch.size < STREAM_FLUSH_BATCH_MAX) {
            val result = channel.tryReceive()
            if (result.isSuccess) {
                batch.add(result.getOrThrow())
            } else {
                if (result.isClosed) closed = true
                break
            }
        }
        if (batch.isNotEmpty()) {
            outputLines.addAll(batch)
            pruneOutput(outputLines)
        }
        if (closed) break
        delay(STREAM_FLUSH_INTERVAL_MS.milliseconds)
    }
}

private fun pruneOutput(outputLines: MutableList<TerminalLine>) {
    val excess = outputLines.size - MAX_OUTPUT_LINES
    if (excess > 0) {
        outputLines.subList(0, excess).clear()
    }
}
