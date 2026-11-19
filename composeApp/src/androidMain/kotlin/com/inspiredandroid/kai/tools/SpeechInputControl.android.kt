package com.inspiredandroid.kai.tools

import android.media.AudioRecord
import android.util.Log
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.inspiredandroid.kai.permissionQualifier
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.speech_input_preparing
import kai.composeapp.generated.resources.speech_input_start
import kai.composeapp.generated.resources.speech_input_stop
import kai.composeapp.generated.resources.speech_input_transcribing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private enum class SpeechInputState {
    Idle,
    Preparing,
    Ready,
    Recording,
    Transcribing,
    Error,
}

@Composable
actual fun SpeechInputControl(
    onTranscript: (String) -> Unit,
    target: VoiceInputTarget,
    modifier: Modifier,
) {
    val context = LocalContext.current.applicationContext
    val permissionController = koinInject<PermissionController>(
        permissionQualifier(AppPermission.RECORD_AUDIO),
    )
    val voiceCoordinator = koinInject<VoiceSessionCoordinator>()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val modelStore = remember(context) { MoonshineModelStore(context) }
    val activeStopRequest = remember { AtomicReference<AtomicBoolean?>(null) }
    val recorderReference = remember { AtomicReference<AudioRecord?>(null) }
    val jobReference = remember { AtomicReference<Job?>(null) }

    var state by remember { mutableStateOf(SpeechInputState.Idle) }

    val startDescription = stringResource(Res.string.speech_input_start)
    val stopDescription = stringResource(Res.string.speech_input_stop)
    val preparingDescription = stringResource(Res.string.speech_input_preparing)
    val transcribingDescription = stringResource(Res.string.speech_input_transcribing)

    fun stopRecording() {
        activeStopRequest.get()?.set(true)
        recorderReference.get()?.let { recorder ->
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                runCatching { recorder.stop() }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // Do not leave the microphone active when Kai moves to the background.
                stopRecording()
                jobReference.getAndSet(null)?.cancel()
                state = SpeechInputState.Idle
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopRecording()
            jobReference.getAndSet(null)?.cancel()
        }
    }

    val isBusy = state == SpeechInputState.Preparing || state == SpeechInputState.Transcribing

    IconButton(
        modifier = modifier,
        enabled = !isBusy,
        onClick = {
            if (state == SpeechInputState.Recording) {
                stopRecording()
            } else {
                // Freeze both destination and callback at the moment dictation starts.
                // Recomposition or terminal-tab changes must not retarget an in-flight result.
                val operationTarget = target
                val operationOnTranscript = onTranscript
                val operationStopRequest = AtomicBoolean(false)
                val speechJob = scope.launch(start = CoroutineStart.LAZY) {
                    var capturedSamples: FloatArray? = null
                    var voiceSession: VoiceSession? = null
                    try {
                        state = SpeechInputState.Preparing
                        if (!permissionController.hasPermission() &&
                            !permissionController.requestPermission()
                        ) {
                            state = SpeechInputState.Error
                            return@launch
                        }

                        // Acquire global voice ownership before model preparation as well as
                        // recording. Otherwise two controls can race to install into the
                        // same shared staging directory on first use.
                        val acquiredSession = voiceCoordinator.begin(
                            mode = VoiceInteractionMode.DICTATION,
                            target = operationTarget,
                        )
                        if (acquiredSession == null) {
                            state = SpeechInputState.Error
                            return@launch
                        }
                        voiceSession = acquiredSession

                        if (!modelStore.isFullyInstalled()) {
                            // First use installs the verified model before recording can begin.
                            // Keep the control visibly busy so the disabled mic is not mistaken
                            // for an unresponsive button during this network operation.
                            Log.i("SpeechInputControl", "Installing verified offline speech model")
                            modelStore.ensureAllInstalled()
                            Log.i("SpeechInputControl", "Offline speech model installation complete")
                            state = SpeechInputState.Ready
                            return@launch
                        }

                        val sessionId = acquiredSession.id
                        val samples = captureSpeech(
                            stopRequested = operationStopRequest,
                            recorderReference = recorderReference,
                            onStarted = {
                                state = SpeechInputState.Recording
                                voiceCoordinator.updatePhase(sessionId, VoiceSessionPhase.LISTENING)
                            },
                        )
                        capturedSamples = samples

                        voiceCoordinator.updatePhase(sessionId, VoiceSessionPhase.PROCESSING)
                        state = SpeechInputState.Transcribing
                        val transcript = withContext(Dispatchers.Default) {
                            transcribeMoonshineV2(modelStore.modelDirectory, samples)
                        }
                        if (transcript.isBlank()) {
                            state = SpeechInputState.Ready
                        } else {
                            // Publish composer state only from the UI dispatcher. The recognizer
                            // runs on Dispatchers.Default; keeping this boundary explicit avoids
                            // a transcript update being deferred until an unrelated configuration
                            // change (for example, rotating the device) triggers recomposition.
                            withContext(Dispatchers.Main.immediate) {
                                Log.i("SpeechInputControl", "Delivering offline transcript to composer")
                                operationOnTranscript(transcript)
                                state = SpeechInputState.Ready
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        // Keep user-facing composer space free of status text while retaining
                        // the failure details for developer diagnostics.
                        currentCoroutineContext().ensureActive()
                        Log.e("SpeechInputControl", "Offline speech input failed", failure)
                        state = SpeechInputState.Error
                    } finally {
                        // Audio exists only for this operation. Wipe the PCM
                        // supplied to the recognizer on success, failure, or cancel.
                        capturedSamples?.fill(0.0f)
                        activeStopRequest.compareAndSet(operationStopRequest, null)
                        jobReference.compareAndSet(currentCoroutineContext()[Job], null)
                        withContext(NonCancellable) {
                            voiceSession?.let { voiceCoordinator.end(it.id) }
                        }
                    }
                }
                // Publish the operation-specific cancellation signal and lazy job before
                // starting it, so an older operation can never clear a newer one.
                activeStopRequest.set(operationStopRequest)
                jobReference.set(speechJob)
                speechJob.start()
            }
        },
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(20.dp)
                    .semantics {
                        contentDescription = if (state == SpeechInputState.Preparing) {
                            preparingDescription
                        } else {
                            transcribingDescription
                        }
                    },
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = if (state == SpeechInputState.Recording) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = if (state == SpeechInputState.Recording) stopDescription else startDescription,
                tint = if (state == SpeechInputState.Recording || state == SpeechInputState.Error) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
