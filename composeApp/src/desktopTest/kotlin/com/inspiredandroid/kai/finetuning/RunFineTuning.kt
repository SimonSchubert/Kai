package com.inspiredandroid.kai.finetuning

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test

/**
 * Orchestrates a Mistral fine-tuning job: uploads training data, creates a job,
 * polls until completion, and prints the resulting model ID.
 *
 * ## How to run
 *
 * ```
 * MISTRAL_API_KEY=your-key-here \
 *   MISTRAL_BASE_MODEL=open-mistral-7b \
 *   ./gradlew :composeApp:desktopTest --tests "*RunFineTuning*" --info
 * ```
 *
 * ## Environment variables
 *
 * | Variable               | Required | Default           | Description                        |
 * |------------------------|----------|-------------------|------------------------------------|
 * | MISTRAL_API_KEY        | Yes      | —                 | Your Mistral API key               |
 * | MISTRAL_BASE_MODEL     | No       | open-mistral-7b   | Base model to fine-tune            |
 * | MISTRAL_TRAINING_STEPS | No       | 100               | Number of training steps           |
 * | MISTRAL_LEARNING_RATE  | No       | 0.0001            | Learning rate                      |
 * | MISTRAL_POLL_INTERVAL  | No       | 30                | Seconds between status polls       |
 * | MISTRAL_AUTO_START     | No       | true              | Auto-start job after creation      |
 *
 * ## Prerequisites
 *
 * Run `GenerateTrainingData` first to produce the JSONL files in `tools/finetuning/output/`.
 */
class RunFineTuning {

    private val apiKey = System.getenv("MISTRAL_API_KEY") ?: ""
    private val baseModel = System.getenv("MISTRAL_BASE_MODEL") ?: "open-mistral-7b"
    private val trainingSteps = System.getenv("MISTRAL_TRAINING_STEPS")?.toIntOrNull() ?: 100
    private val learningRate = System.getenv("MISTRAL_LEARNING_RATE")?.toDoubleOrNull() ?: 0.0001
    private val pollIntervalSeconds = System.getenv("MISTRAL_POLL_INTERVAL")?.toLongOrNull() ?: 30L
    private val autoStart = System.getenv("MISTRAL_AUTO_START")?.toBooleanStrictOrNull() ?: true

    private val jsonSerializer = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(jsonSerializer)
        }
        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    // Only log errors
                }
            }
            level = LogLevel.NONE
        }
    }

    // region DTOs -----------------------------------------------------------------------------

    @Serializable
    private data class FileUploadResponse(
        val id: String,
        val filename: String = "",
        val bytes: Long = 0,
        val purpose: String = "",
    )

    @Serializable
    private data class TrainingFile(
        val file_id: String,
        val weight: Int = 1,
    )

    @Serializable
    private data class FineTuningJobRequest(
        val model: String,
        val training_files: List<TrainingFile>,
        val validation_files: List<String>? = null,
        val hyperparameters: Hyperparameters? = null,
        val auto_start: Boolean = true,
    )

    @Serializable
    private data class Hyperparameters(
        val training_steps: Int? = null,
        val learning_rate: Double? = null,
    )

    @Serializable
    private data class FineTuningJobResponse(
        val id: String,
        val model: String = "",
        val status: String = "",
        val fine_tuned_model: String? = null,
        val created_at: Long = 0,
        val modified_at: Long = 0,
        val checkpoints: List<Checkpoint> = emptyList(),
        val events: List<JobEvent> = emptyList(),
    )

    @Serializable
    private data class Checkpoint(
        val metrics: CheckpointMetrics? = null,
        val step_number: Int = 0,
    )

    @Serializable
    private data class CheckpointMetrics(
        val train_loss: Double? = null,
        val valid_loss: Double? = null,
        val valid_mean_token_accuracy: Double? = null,
    )

    @Serializable
    private data class JobEvent(
        val name: String? = null,
        val created_at: Long = 0,
    )

    // endregion

    @Test
    fun `run fine-tuning job`() = runBlocking {
        if (apiKey.isBlank()) {
            println("[RunFineTuning] Skipped — set MISTRAL_API_KEY to run.")
            return@runBlocking
        }

        val projectRoot = findProjectRoot()
        val outputDir = File(projectRoot, "tools/finetuning/output")
        val trainingFile = File(outputDir, "training_data.jsonl")
        val validationFile = File(outputDir, "validation_data.jsonl")

        if (!trainingFile.exists()) {
            println("[RunFineTuning] Training data not found at ${trainingFile.absolutePath}")
            println("    Run GenerateTrainingData first.")
            return@runBlocking
        }

        println("[RunFineTuning] === Mistral Fine-Tuning ===")
        println("  Base model:     $baseModel")
        println("  Training steps: $trainingSteps")
        println("  Learning rate:  $learningRate")
        println()

        // Step 1: Upload training file
        println("[Step 1] Uploading training data...")
        val trainingFileId = uploadFile(trainingFile)
        println("  Training file ID: $trainingFileId")

        // Step 2: Upload validation file (optional)
        var validationFileId: String? = null
        if (validationFile.exists()) {
            println("[Step 2] Uploading validation data...")
            validationFileId = uploadFile(validationFile)
            println("  Validation file ID: $validationFileId")
        } else {
            println("[Step 2] No validation file found, skipping.")
        }

        // Step 3: Create fine-tuning job
        println("[Step 3] Creating fine-tuning job...")
        val job = createJob(trainingFileId, validationFileId)
        println("  Job ID: ${job.id}")
        println("  Status: ${job.status}")
        println()

        // Step 4: Poll until completion
        println("[Step 4] Monitoring job progress...")
        val finalJob = pollJob(job.id)

        println()
        println("[RunFineTuning] === RESULT ===")
        println("  Status:           ${finalJob.status}")
        if (finalJob.fine_tuned_model != null) {
            println("  Fine-tuned model: ${finalJob.fine_tuned_model}")
            println()
            println("  To use this model in Kai:")
            println("  1. Add or select the Mistral service in Settings")
            println("  2. The fine-tuned model will appear in the model dropdown")
            println("  3. Select: ${finalJob.fine_tuned_model}")
            println()
            println("  To validate with the integration test:")
            println("  KAI_INTEGRATION=1 KAI_MISTRAL_FT_KEY=${"$"}MISTRAL_API_KEY KAI_MISTRAL_FT_MODEL=${finalJob.fine_tuned_model} \\")
            println("    ./gradlew :composeApp:desktopTest --tests \"*KaiUiValidationTest*\" --info")
        } else {
            println("  Fine-tuned model: (not available — job may have failed)")
            if (finalJob.events.isNotEmpty()) {
                println("  Events:")
                for (event in finalJob.events) {
                    println("    - ${event.name} (at ${event.created_at})")
                }
            }
        }
    }

    // region API calls ------------------------------------------------------------------------

    private suspend fun uploadFile(file: File): String {
        val response = client.submitFormWithBinaryData(
            url = "https://api.mistral.ai/v1/files",
            formData = formData {
                append("purpose", "fine-tune")
                append(
                    "file",
                    file.readBytes(),
                    Headers.build {
                        append(HttpHeaders.ContentDisposition, "filename=\"${file.name}\"")
                        append(HttpHeaders.ContentType, "application/jsonl")
                    },
                )
            },
        ) {
            bearerAuth(apiKey)
            timeout {
                requestTimeoutMillis = 300_000 // 5 minutes for large files
            }
        }

        if (!response.status.isSuccess()) {
            error("File upload failed (${response.status}): ${response.bodyAsText()}")
        }

        return response.body<FileUploadResponse>().id
    }

    private suspend fun createJob(trainingFileId: String, validationFileId: String?): FineTuningJobResponse {
        val request = FineTuningJobRequest(
            model = baseModel,
            training_files = listOf(TrainingFile(file_id = trainingFileId)),
            validation_files = validationFileId?.let { listOf(it) },
            hyperparameters = Hyperparameters(
                training_steps = trainingSteps,
                learning_rate = learningRate,
            ),
            auto_start = autoStart,
        )

        val response = client.post("https://api.mistral.ai/v1/fine_tuning/jobs") {
            bearerAuth(apiKey)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            error("Job creation failed (${response.status}): ${response.bodyAsText()}")
        }

        return response.body()
    }

    private suspend fun getJob(jobId: String): FineTuningJobResponse {
        val response = client.get("https://api.mistral.ai/v1/fine_tuning/jobs/$jobId") {
            bearerAuth(apiKey)
        }

        if (!response.status.isSuccess()) {
            error("Job status check failed (${response.status}): ${response.bodyAsText()}")
        }

        return response.body()
    }

    private suspend fun pollJob(jobId: String): FineTuningJobResponse {
        var lastStatus = ""
        var lastCheckpointCount = 0

        while (true) {
            val job = getJob(jobId)

            // Print status changes
            if (job.status != lastStatus) {
                println("  [${java.time.LocalTime.now().toString().take(8)}] Status: ${job.status}")
                lastStatus = job.status
            }

            // Print new checkpoints
            if (job.checkpoints.size > lastCheckpointCount) {
                val newCheckpoints = job.checkpoints.drop(lastCheckpointCount)
                for (cp in newCheckpoints) {
                    val trainLoss = cp.metrics?.train_loss?.let { "%.6f".format(it) } ?: "—"
                    val validLoss = cp.metrics?.valid_loss?.let { "%.6f".format(it) } ?: "—"
                    println("  [Step ${cp.step_number}] train_loss=$trainLoss  valid_loss=$validLoss")
                }
                lastCheckpointCount = job.checkpoints.size
            }

            // Check terminal states
            when (job.status.uppercase()) {
                "SUCCESS", "SUCCEEDED" -> return job
                "FAILED", "CANCELLED" -> return job
            }

            delay(pollIntervalSeconds * 1000)
        }
    }

    // endregion

    private fun findProjectRoot(): File {
        var dir = File(System.getProperty("user.dir"))
        while (dir.parentFile != null) {
            if (File(dir, "settings.gradle.kts").exists() && File(dir, "composeApp").exists()) {
                return dir
            }
            dir = dir.parentFile
        }
        return File(System.getProperty("user.dir"))
    }
}
