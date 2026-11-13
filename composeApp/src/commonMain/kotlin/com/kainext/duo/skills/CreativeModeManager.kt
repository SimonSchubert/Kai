package com.kainext.duo.skills

import com.kainext.duo.skills.SkillManager.SkillDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 对话式“创造模式”管理器。
 *
 * 用户在聊天中开启创造模式后，可与子智能交互共同定义一个新 skill：
 * 子智能会询问技能名称、描述与用途，逐步拼装 [SkillDraft]，用户随时可
 * 预览生成的 SKILL.md 并点击“保存”写入 ~/skills/<id>/SKILL.md，随后该技能
 * 立即可在对话中以 /<id> 调用。
 *
 * 状态机为单步对话流：Idle -> Collecting -> ReadyToSave -> Saved，每一步由 UI 层
 * 渲染对话气泡，子智能负责向用户提问并把答案填入 draft。
 */
class CreativeModeManager(
    private val skillManager: SkillManager,
) {

    /** 创造模式会话的当前阶段。 */
    enum class Stage {
        /** 未开始：尚未与子智能交互。 */
        Idle,
        /** 收集中：子智能正在询问名称/描述/用途。 */
        Collecting,
        /** 草稿已就绪：可预览并保存。 */
        ReadyToSave,
        /** 已保存：skill 已写入沙箱并重新加载。 */
        Saved,
    }

    private val _stage = MutableStateFlow(Stage.Idle)
    val stage: StateFlow<Stage> = _stage.asStateFlow()

    private val _draft = MutableStateFlow<SkillDraft?>(null)
    val draft: StateFlow<SkillDraft?> = _draft.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var lastError: String? = null

    /** 开始一轮创造：重置状态，由子智能发出第一个问题。 */
    fun start() {
        _stage.value = Stage.Collecting
        _draft.value = null
        _message.value = "创造模式已开启。请告诉我这个新 skill 应该叫什么名字？（只能用小写字母、数字和连字符，最多 64 个字符）"
        lastError = null
    }

    /** 用户提交一条回复，由子智能按当前阶段解析并推进 draft。 */
    fun submitUserReply(reply: String) {
        when (_stage.value) {
            Stage.Collecting -> collectStep(reply)
            Stage.ReadyToSave -> { /* 保存前的文本回复不触发状态变更，需点击保存按钮 */ }
            Stage.Saved -> { /* 已结束，不再接受新输入 */ }
            Stage.Idle -> start()
        }
    }

    /** 预览当前草稿会生成的 SKILL.md 内容。 */
    fun preview(): String? {
        val d = _draft.value ?: return null
        return buildString {
            appendLine("---\nname: \"${d.name}\"\ndescription: \"${d.description}\"\n---")
            appendLine(d.body.trimEnd())
            appendLine("")
        }
    }

    /** 保存当前草稿到沙箱并重新加载技能缓存。 */
    suspend fun save(): Result<SkillManifest> {
        val d = _draft.value ?: return Result.failure(IllegalStateException("没有可保存的草稿"))
        val result = skillManager.createSkill(d)
        if (result.isSuccess) {
            _stage.value = Stage.Saved
            _message.value = "技能 '/${result.getOrNull()?.id ?: d.name}' 已创建并安装，现在可在对话中用 /技能名 调用。"
        } else {
            lastError = result.exceptionOrNull()?.message ?: "保存失败"
            _message.value = lastError
        }
        return result
    }

    /** 取消当前创造会话，回到空闲。 */
    fun cancel() {
        _stage.value = Stage.Idle
        _draft.value = null
        _message.value = null
        lastError = null
    }

    /** 子智能在对话中填充名称与描述，由 UI 层或子智能调用。 */
    fun setDraft(name: String, description: String, body: String = "") {
        _draft.value = SkillDraft(name, description, body)
        _stage.value = Stage.ReadyToSave
        _message.value = "草稿已就绪：名称 '$name'，描述 '$description'。预览无误后点击“保存”即可安装到 Kai。"
    }

    private fun collectStep(reply: String) {
        val trimmed = reply.trim()
        if (trimmed.isEmpty()) {
            _message.value = "请输入非空内容。"
            return
        }
        val current = _draft.value
        if (current == null) {
            val candidate = trimmed.lowercase().replace(Regex("[^a-z0-9-]"), "-")
            if (candidate.isNotEmpty() && candidate.length <= 64) {
                _draft.value = SkillDraft(candidate, "", "")
                _message.value = "技能名称已记为 '$candidate'。请简要说明这个 skill 做什么（用于前置提示和用户理解）。"
            } else {
                _message.value = "名称不合法，请用小写字母、数字和连字符组成，最多 64 个字符。"
            }
        } else if (current.description.isEmpty()) {
            // Report the value that was just captured: `current` is the pre-copy
            // draft, whose description is still empty at this point.
            val updated = current.copy(description = trimmed.take(1024))
            _draft.value = updated
            _stage.value = Stage.ReadyToSave
            _message.value = "技能描述已记为 '${updated.description}'。草稿已就绪，预览无误后点击“保存”。"
        } else {
            _draft.value = current.copy(body = (current.body + "\n" + trimmed).trim())
            _message.value = "正文已追加。继续输入更多内容，或点击“保存”安装。"
        }
    }
}
