package com.inspiredandroid.kai.tools

import com.inspiredandroid.kai.network.tools.ParameterSchema
import com.inspiredandroid.kai.network.tools.Tool
import com.inspiredandroid.kai.network.tools.ToolInfo
import com.inspiredandroid.kai.network.tools.ToolSchema
import com.inspiredandroid.kai.sandbox.LinuxSandboxManager
import com.inspiredandroid.kai.sandbox.SandboxState
import com.inspiredandroid.kai.sandbox.SshConfigManager
import org.koin.java.KoinJavaComponent.inject
import java.io.File

private const val TOOL_DESCRIPTION = """Register a named SSH host alias in the Linux sandbox so subsequent execute_shell_command calls can run `ssh <alias>` instead of repeating user/host/port/identity flags every time.

What this writes inside the sandbox:
- ~/.ssh/config: a Host block for the alias. Calling again with the same alias replaces the previous block (idempotent).
- Defaults block at the top of the config on first use: ServerAliveInterval + ServerAliveCountMax (keep idle TCP connections alive through NAT) and StrictHostKeyChecking=accept-new (auto-accept new host keys into ~/.ssh/known_hosts on first connect, but still reject changed keys — sane TOFU without an interactive prompt this shell can't answer).
- Optionally appends a line to ~/.ssh/known_hosts to skip the first-connect TOFU step entirely.

This tool does NOT create or upload private keys. To make a key usable, the user must place it under ~/.ssh in the sandbox separately. Be aware that any key text passed through chat (including via execute_shell_command's `cat > ~/.ssh/id_x <<EOF ...`) goes to the model provider in cleartext — ask the user before doing that.

Password-only remotes: openssh inside this sandbox can't field interactive password prompts on its own (no PTY; ssh reads from /dev/tty, not stdin, so heredoc fallback does not work). Install sshpass once (`apk add sshpass` via execute_shell_command) and invoke as `sshpass -p '<password>' ssh <alias> '<remote-cmd>'`, or `sshpass -f <file> ssh <alias>` to keep the password out of the command line. sshpass fakes a PTY internally, which is the only path that actually delivers a password.

Connection persistence ("held connections") is NOT available — openssh's ControlMaster multiplexing requires the link() syscall to create its control socket, and Android blocks link() for app processes regardless of file ownership. Each ssh call does a full handshake. Don't fight this; don't try to seed your own ControlPath.

After configuring, drive ssh from execute_shell_command:
- `ssh myalias 'remote cmd'`
- `scp file myalias:`
- `sftp myalias`
Auth, port, identity all come from the config block — no flags needed. ALWAYS invoke by the alias, never `user@hostname`; bypassing the alias bypasses every setting this tool just wrote."""

object SshConfigureHostTool : Tool {

    private val sandboxManager: LinuxSandboxManager by inject(LinuxSandboxManager::class.java)

    override val schema = ToolSchema(
        name = "ssh_configure_host",
        description = TOOL_DESCRIPTION,
        parameters = mapOf(
            "alias" to ParameterSchema(
                "string",
                "Short name used to invoke this host (e.g. 'prod', 'my-vps'). Must contain no whitespace.",
                true,
            ),
            "hostname" to ParameterSchema(
                "string",
                "DNS name or IP of the remote machine.",
                true,
            ),
            "user" to ParameterSchema(
                "string",
                "SSH user. Omit to fall back to the openssh default (current user inside the sandbox).",
                false,
            ),
            "port" to ParameterSchema(
                "integer",
                "SSH port. Omit for 22.",
                false,
            ),
            "identity_file" to ParameterSchema(
                "string",
                "Path to the private key inside the sandbox. Relative names resolve under ~/.ssh (e.g. 'my-vps_id' → ~/.ssh/my-vps_id); absolute paths and ~-paths pass through. The file is not created by this tool.",
                false,
            ),
            "known_host_line" to ParameterSchema(
                "string",
                "Optional single line appended to ~/.ssh/known_hosts. Typically the output of `ssh-keyscan -t ed25519 <host>`. Deduplicated by exact-line match.",
                false,
            ),
        ),
    )

    override suspend fun execute(args: Map<String, Any>): Any {
        if (sandboxManager.state.value !is SandboxState.Ready) {
            return mapOf(
                "success" to false,
                "error" to "Linux sandbox is not installed. Set it up in Settings > Tools.",
            )
        }
        val alias = (args["alias"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return mapOf("success" to false, "error" to "alias is required")
        val hostname = (args["hostname"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return mapOf("success" to false, "error" to "hostname is required")
        val user = (args["user"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val port = (args["port"] as? Number)?.toInt()
        val identityFile = (args["identity_file"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val knownHostLine = (args["known_host_line"] as? String)?.trim()?.takeIf { it.isNotEmpty() }

        val manager = SshConfigManager(File(sandboxManager.homePath))
        return try {
            val configChanged = manager.upsertHost(alias, hostname, user, port, identityFile)
            val knownHostsChanged = knownHostLine?.let { manager.appendKnownHostLine(it) } ?: false
            mapOf(
                "success" to true,
                "alias" to alias,
                "config_changed" to configChanged,
                "known_hosts_changed" to knownHostsChanged,
                "example" to "ssh $alias",
            )
        } catch (e: IllegalArgumentException) {
            mapOf("success" to false, "error" to (e.message ?: "Invalid argument"))
        } catch (e: Exception) {
            mapOf("success" to false, "error" to "Failed to write ssh config: ${e.message}")
        }
    }

    val toolInfo = ToolInfo(
        id = "ssh_configure_host",
        name = "Configure SSH Host",
        description = "Register a named SSH host for the Linux sandbox",
        nameRes = null,
        descriptionRes = null,
        isEnabled = false,
    )
}
