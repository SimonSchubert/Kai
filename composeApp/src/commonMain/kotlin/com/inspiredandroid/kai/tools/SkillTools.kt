package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.data.SkillExecutor
import com.inspiredandroid.kai.data.SkillStore
import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import kai.composeapp.generated.resources.Res
import kai.composeapp.generated.resources.tool_skill_create_description
import kai.composeapp.generated.resources.tool_skill_create_name
import kai.composeapp.generated.resources.tool_skill_delete_description
import kai.composeapp.generated.resources.tool_skill_delete_name
import kai.composeapp.generated.resources.tool_skill_execute_description
import kai.composeapp.generated.resources.tool_skill_execute_name
import kai.composeapp.generated.resources.tool_skill_list_description
import kai.composeapp.generated.resources.tool_skill_list_name
import kai.composeapp.generated.resources.tool_skill_update_data_description
import kai.composeapp.generated.resources.tool_skill_update_data_name
import kotlin.time.Duration.Companion.seconds

object SkillTools {

    fun skillCreateTool(skillStore: SkillStore, skillExecutor: SkillExecutor) = object : Tool {
        override val schema = ToolSchema(
            name = "skill_create",
            description = "Create or update a reusable skill script written in JavaScript (ES2023). " +
                "Skills are saved persistently and can be executed later with skill_execute. " +
                "IMPORTANT: The variable 'input' is always available at runtime — it contains the value passed via skill_execute. " +
                "Scripts MUST use the 'input' variable for any dynamic data (e.g. city name, search query, file path) instead of hardcoding values. " +
                "If input is not provided it will be null. Example: const city = input || 'default'; " +
                "The return value of the last expression is the skill's output. " +
                "Available variables: 'input' (dynamic data from caller), 'data' (skill's stored JSON config, set via the data parameter). " +
                "Available APIs: " +
                "fetch(url, options?) - async HTTP (returns {ok, status, body, json(), text()}), " +
                "fs.readFile(path)/fs.writeFile(path,content)/fs.exists(path)/fs.listDir(path) - file system, " +
                "skill.run(name, input?) - call another skill and get its output (max 5 levels deep). " +
                "Use top-level await for fetch and skill.run calls.",
            parameters = mapOf(
                "name" to ParameterSchema(type = "string", description = "Unique name for the skill (e.g. 'reverse_text', 'parse_csv')", required = true),
                "description" to ParameterSchema(type = "string", description = "Human-readable description of what the skill does", required = true),
                "script" to ParameterSchema(type = "string", description = "The JavaScript source code for the skill", required = true),
                "readme" to ParameterSchema(type = "string", description = "Markdown documentation — usage examples, expected inputs, behavior notes", required = false),
                "data" to ParameterSchema(type = "string", description = "JSON config/data accessible in the script as the 'data' variable (e.g. '{\"default_city\":\"Berlin\",\"unit\":\"metric\"}')", required = false),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val name = args["name"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing name")
            val description = args["description"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing description")
            val script = args["script"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing script")
            val readme = args["readme"]?.toString() ?: ""
            val data = args["data"]?.toString() ?: ""

            val syntaxError = skillExecutor.validate(script)
            if (syntaxError != null) {
                return mapOf("success" to false, "error" to "Script has syntax errors: $syntaxError")
            }

            val entry = skillStore.saveSkill(
                name = name,
                description = description,
                script = script,
                readme = readme,
                dataJson = data,
            )
            return mapOf(
                "success" to true,
                "name" to entry.name,
                "description" to entry.description,
                "id" to entry.id,
            )
        }
    }

    fun skillListTool(skillStore: SkillStore) = object : Tool {
        override val schema = ToolSchema(
            name = "skill_list",
            description = "List all saved skills with their names, descriptions, and usage statistics.",
            parameters = emptyMap(),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val skills = skillStore.getAllSkills()
            return mapOf(
                "success" to true,
                "count" to skills.size,
                "skills" to skills.map { skill ->
                    mapOf(
                        "name" to skill.name,
                        "description" to skill.description,
                        "execution_count" to skill.executionCount,
                        "has_readme" to skill.readme.isNotEmpty(),
                        "has_data" to skill.dataJson.isNotEmpty(),
                    )
                },
            )
        }
    }

    fun skillExecuteTool(skillStore: SkillStore, skillExecutor: SkillExecutor) = object : Tool {
        override val schema = ToolSchema(
            name = "skill_execute",
            description = "Execute a saved skill script by name. The script runs in a sandboxed JavaScript environment. " +
                "IMPORTANT: Always pass the dynamic data (e.g. city name, query, URL) via the 'input' parameter — " +
                "the script accesses it as the 'input' variable. Do NOT rely on hardcoded values in the script.",
            parameters = mapOf(
                "name" to ParameterSchema(type = "string", description = "Name of the skill to execute", required = true),
                "input" to ParameterSchema(type = "string", description = "Input data to pass to the script (accessible as 'input' variable in JS)", required = false),
            ),
        )

        override val timeout = 130.seconds

        override suspend fun execute(args: Map<String, Any>): Any {
            val name = args["name"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing name")
            val input = args["input"]?.toString()

            val skill = skillStore.getSkill(name)
                ?: return mapOf("success" to false, "error" to "Skill not found: $name")

            val result = skillExecutor.execute(
                script = skill.script,
                input = input,
                dataJson = skill.dataJson.ifEmpty { null },
            )

            skillStore.recordExecution(name, result.output.ifEmpty { result.error ?: "" })

            return mapOf(
                "success" to result.success,
                "output" to result.output,
                "error" to (result.error ?: ""),
                "timed_out" to result.timedOut,
            )
        }
    }

    fun skillUpdateDataTool(skillStore: SkillStore) = object : Tool {
        override val schema = ToolSchema(
            name = "skill_update_data",
            description = "Update the data/configuration of an existing skill without changing its script. " +
                "The data is accessible in the script as the 'data' variable (parsed JSON object).",
            parameters = mapOf(
                "name" to ParameterSchema(type = "string", description = "Name of the skill to update", required = true),
                "data" to ParameterSchema(type = "string", description = "New JSON string for the skill's data", required = true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val name = args["name"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing name")
            val data = args["data"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing data")

            val updated = skillStore.updateData(name, data)
                ?: return mapOf("success" to false, "error" to "Skill not found: $name")
            return mapOf("success" to true, "name" to updated.name)
        }
    }

    fun skillDeleteTool(skillStore: SkillStore) = object : Tool {
        override val schema = ToolSchema(
            name = "skill_delete",
            description = "Delete a saved skill by name.",
            parameters = mapOf(
                "name" to ParameterSchema(type = "string", description = "Name of the skill to delete", required = true),
            ),
        )

        override suspend fun execute(args: Map<String, Any>): Any {
            val name = args["name"]?.toString()
                ?: return mapOf("success" to false, "error" to "Missing name")

            val removed = skillStore.deleteSkill(name)
            return if (removed) {
                mapOf("success" to true, "name" to name, "status" to "DELETED")
            } else {
                mapOf("success" to false, "error" to "Skill not found: $name")
            }
        }
    }

    val skillCreateToolInfo = ToolInfo(
        id = "skill_create",
        name = "Create Skill",
        description = "Create or update a reusable JavaScript skill",
        nameRes = Res.string.tool_skill_create_name,
        descriptionRes = Res.string.tool_skill_create_description,
    )

    val skillListToolInfo = ToolInfo(
        id = "skill_list",
        name = "List Skills",
        description = "List all saved skills",
        nameRes = Res.string.tool_skill_list_name,
        descriptionRes = Res.string.tool_skill_list_description,
    )

    val skillExecuteToolInfo = ToolInfo(
        id = "skill_execute",
        name = "Execute Skill",
        description = "Execute a saved skill by name",
        nameRes = Res.string.tool_skill_execute_name,
        descriptionRes = Res.string.tool_skill_execute_description,
    )

    val skillUpdateDataToolInfo = ToolInfo(
        id = "skill_update_data",
        name = "Update Skill Data",
        description = "Update a skill's configuration data",
        nameRes = Res.string.tool_skill_update_data_name,
        descriptionRes = Res.string.tool_skill_update_data_description,
    )

    val skillDeleteToolInfo = ToolInfo(
        id = "skill_delete",
        name = "Delete Skill",
        description = "Delete a saved skill",
        nameRes = Res.string.tool_skill_delete_name,
        descriptionRes = Res.string.tool_skill_delete_description,
    )

    val skillToolDefinitions = listOf(skillCreateToolInfo, skillListToolInfo, skillExecuteToolInfo, skillUpdateDataToolInfo, skillDeleteToolInfo)

    fun getSkillTools(skillStore: SkillStore, skillExecutor: SkillExecutor): List<Tool> = listOf(
        skillCreateTool(skillStore, skillExecutor),
        skillListTool(skillStore),
        skillExecuteTool(skillStore, skillExecutor),
        skillUpdateDataTool(skillStore),
        skillDeleteTool(skillStore),
    )
}
