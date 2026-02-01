package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.data.AppSettings
import com.inspiredandroid.kai.httpClient
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

@Serializable
private data class IpLocationResponse(
    val status: String,
    val country: String? = null,
    val countryCode: String? = null,
    val region: String? = null,
    val regionName: String? = null,
    val city: String? = null,
    val zip: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val timezone: String? = null,
    val isp: String? = null,
    val query: String? = null,
    val message: String? = null,
)

/**
 * Common tool definitions that work across all platforms.
 */
object CommonTools {

    private val locationClient = httpClient {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
        }
    }

    val ipLocationTool = object : Tool {
        override val schema = ToolSchema(
            name = "get_location_from_ip",
            description = "Get the user's estimated location based on their IP address. Returns city, region, country, coordinates, and timezone.",
            parameters = emptyMap(),
        )

        override suspend fun execute(args: Map<String, Any>): Any = try {
            val response: IpLocationResponse = locationClient.get("http://ip-api.com/json/").body()
            if (response.status == "success") {
                mapOf(
                    "success" to true,
                    "city" to response.city,
                    "region" to response.regionName,
                    "country" to response.country,
                    "country_code" to response.countryCode,
                    "latitude" to response.lat,
                    "longitude" to response.lon,
                    "timezone" to response.timezone,
                    "zip" to response.zip,
                    "isp" to response.isp,
                    "ip" to response.query,
                )
            } else {
                mapOf(
                    "success" to false,
                    "error" to (response.message ?: "Failed to get location"),
                )
            }
        } catch (e: Exception) {
            mapOf(
                "success" to false,
                "error" to "Failed to get location: ${e.message}",
            )
        }
    }

    val ipLocationToolInfo = ToolInfo(
        id = "get_location_from_ip",
        name = "Get Location",
        description = "Get estimated location from IP address",
    )

    val localTimeTool = object : Tool {
        override val schema = ToolSchema(
            name = "get_local_time",
            description = "Get the current local date and time. Call this first when the user mentions relative dates like 'tomorrow', 'next week', 'in 2 hours', etc.",
            parameters = emptyMap(),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val timeZone = TimeZone.currentSystemDefault()
            val now = Clock.System.now()
            val localDateTime = now.toLocalDateTime(timeZone)

            // Format display string manually since kotlinx-datetime doesn't have formatters
            val dayOfWeek = localDateTime.dayOfWeek.name.lowercase()
                .replaceFirstChar { it.uppercase() }
            val month = localDateTime.month.name.lowercase()
                .replaceFirstChar { it.uppercase() }
            val day = localDateTime.date.day
            val year = localDateTime.year
            val hour = localDateTime.hour
            val minute = localDateTime.minute.toString().padStart(2, '0')
            val amPm = if (hour < 12) "AM" else "PM"
            val hour12 = when {
                hour == 0 -> 12
                hour > 12 -> hour - 12
                else -> hour
            }

            return mapOf(
                "iso_datetime" to "${localDateTime.date}T${localDateTime.hour.toString().padStart(2, '0')}:$minute:${localDateTime.second.toString().padStart(2, '0')}",
                "display_datetime" to "$dayOfWeek, $month $day, $year at $hour12:$minute $amPm",
                "timezone" to timeZone.id,
                "day_of_week" to localDateTime.dayOfWeek.name,
            )
        }
    }

    val localTimeToolInfo = ToolInfo(
        id = "get_local_time",
        name = "Get Local Time",
        description = "Get the current local date and time for interpreting relative dates",
    )

    val commonToolDefinitions = listOf(localTimeToolInfo, ipLocationToolInfo)

    fun getCommonTools(appSettings: AppSettings): List<Tool> = buildList {
        if (appSettings.isToolEnabled(localTimeTool.schema.name)) {
            add(localTimeTool)
        }
        if (appSettings.isToolEnabled(ipLocationTool.schema.name)) {
            add(ipLocationTool)
        }
    }
}
