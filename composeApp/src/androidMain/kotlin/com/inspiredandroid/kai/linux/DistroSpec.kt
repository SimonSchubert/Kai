package com.inspiredandroid.kai.linux

import android.os.Build
import java.io.File
import java.io.IOException
import java.net.URL

// Cap at 3.22: Alpine 3.23+ ships apk-tools 3, which uses execveat() in a way
// proot does not support, so `apk update` fails under the sandbox runtime.
// See termux/proot-distro#532 / #595.
private const val ALPINE_VERSION = "3.22.5"
private const val ALPINE_BRANCH = "v3.22"

private val ALPINE_MIRRORS = listOf(
    "https://dl-cdn.alpinelinux.org/alpine",
    "https://mirrors.edge.kernel.org/alpine",
    "https://ftp.halifax.rwth-aachen.de/alpine",
    "https://alpine.ethz.ch/alpine",
    "https://mirror.csclub.uwaterloo.ca/alpine",
    "https://mirrors.tuna.tsinghua.edu.cn/alpine",
)

private const val LXC_INDEX = "https://images.linuxcontainers.org/meta/1.0/index-user"
private const val LXC_BASE = "https://images.linuxcontainers.org"
private const val DEBIAN_RELEASE = "bookworm"

/**
 * The per-distribution facts the shared installer and proot launcher need:
 * where the rootfs comes from, what has to be fixed up after extraction, and
 * which proot flags and environment the distro's own tooling depends on.
 */
sealed interface DistroSpec {

    val distro: LinuxDistro

    /** This device's ABI in the distro's own architecture vocabulary. */
    fun arch(): String

    /** File name for the downloaded archive — the extension picks the decompressor. */
    val archiveName: String

    /**
     * Candidate download URLs, best first. May hit the network to resolve an
     * index, so call it off the main thread.
     */
    fun rootfsUrls(): List<String>

    /** Post-extraction fixes that must land before the first proot run. */
    fun configure(rootfsDir: File)

    /** proot flags this distro cannot work without. */
    val prootArgs: List<String> get() = emptyList()

    /** Environment every command in this distro should see. */
    val env: Map<String, String> get() = emptyMap()

    companion object {
        fun of(distro: LinuxDistro): DistroSpec = when (distro) {
            LinuxDistro.ALPINE -> AlpineSpec
            LinuxDistro.DEBIAN -> DebianSpec
        }
    }
}

object AlpineSpec : DistroSpec {

    override val distro = LinuxDistro.ALPINE

    override val archiveName = "rootfs.tar.gz"

    override fun arch(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        return when {
            abi.startsWith("arm64") -> "aarch64"
            abi.startsWith("armeabi") -> "armhf"
            abi.startsWith("x86_64") -> "x86_64"
            abi.startsWith("x86") -> "x86"
            else -> "aarch64"
        }
    }

    override fun rootfsUrls(): List<String> {
        val arch = arch()
        return ALPINE_MIRRORS.map { base ->
            "$base/$ALPINE_BRANCH/releases/$arch/alpine-minirootfs-$ALPINE_VERSION-$arch.tar.gz"
        }
    }

    override fun configure(rootfsDir: File) {
        TarExtractor.makeWritable(rootfsDir)
        TarExtractor.writeResolvConf(rootfsDir)
        writeRepositories(rootfsDir, ALPINE_MIRRORS.first())
    }

    /**
     * `apk update` is the first thing that has to work, and a mirror can be
     * unreachable even when the one that served the rootfs was fine. The
     * installer walks these, rewriting `repositories` each time.
     */
    val mirrors: List<String> = ALPINE_MIRRORS

    fun writeRepositories(rootfsDir: File, mirrorBase: String) {
        val apkDir = File(rootfsDir, "etc/apk")
        apkDir.mkdirs()
        File(apkDir, "repositories").writeText(
            "$mirrorBase/$ALPINE_BRANCH/main\n$mirrorBase/$ALPINE_BRANCH/community\n",
        )
    }
}

object DebianSpec : DistroSpec {

    override val distro = LinuxDistro.DEBIAN

    override val archiveName = "rootfs.tar.xz"

    override fun arch(): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        return when {
            abi.startsWith("arm64") -> "arm64"
            abi.startsWith("armeabi") -> "armhf"
            abi.startsWith("x86_64") -> "amd64"
            abi.startsWith("x86") -> "i386"
            else -> "arm64"
        }
    }

    /**
     * Newest default image for this device's architecture, e.g.
     * `debian;bookworm;arm64;default;20260731_05:24;/images/debian/bookworm/arm64/default/20260731_05:24/`.
     */
    override fun rootfsUrls(): List<String> {
        val arch = arch()
        val index = URL(LXC_INDEX).openStream().bufferedReader().use { it.readText() }
        val line = index.lineSequence()
            .filter { it.startsWith("debian;$DEBIAN_RELEASE;$arch;default;") }
            .maxOrNull()
            ?: throw IOException("No Debian $DEBIAN_RELEASE image for $arch in LXC index")
        val path = line.split(';').getOrNull(5)?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IOException("Malformed LXC index line: $line")
        return listOf(LXC_BASE + path.removeSuffix("/") + "/rootfs.tar.xz")
    }

    override fun configure(rootfsDir: File) {
        TarExtractor.makeWritable(rootfsDir)
        TarExtractor.writeResolvConf(rootfsDir)
        // apt and dpkg assume these exist; an LXC image ships some of them empty
        // and the tar extractor skips empty directories it never saw an entry for.
        listOf(
            "var/lib/apt/lists/partial",
            "var/cache/apt/archives/partial",
            "var/lib/dpkg/updates",
            "var/lib/dpkg/info",
            "var/lib/dpkg/alternatives",
            "var/log",
            "run/lock",
            "tmp",
        ).forEach { File(rootfsDir, it).mkdirs() }
        // dpkg fsyncs every unpacked file by default, which on a phone turns a
        // base install into a multi-minute affair.
        File(rootfsDir, "etc/dpkg/dpkg.cfg.d").mkdirs()
        File(rootfsDir, "etc/dpkg/dpkg.cfg.d/force-unsafe-io").writeText("force-unsafe-io\n")
    }

    /**
     * dpkg unpacks packages via hardlinks, which Android's `protected_hardlinks`
     * policy refuses inside the app sandbox. Without the emulation the base
     * install fails with a dpkg subprocess error even though `apt-get update`
     * succeeded. `-L` is the companion lstat fix.
     */
    override val prootArgs = listOf("--link2symlink", "-L")

    override val env = mapOf("DEBIAN_FRONTEND" to "noninteractive")
}
