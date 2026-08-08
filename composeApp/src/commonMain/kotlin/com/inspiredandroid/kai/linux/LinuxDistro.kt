package com.inspiredandroid.kai.linux

/**
 * The Linux distributions Kai can run under proot.
 *
 * Kai Build is always [DEBIAN] — its coding agents are vendor scripts that expect
 * glibc and apt. The chat sandbox lets the user pick, and when it is also Debian
 * the two share a single rootfs instead of installing one each.
 */
enum class LinuxDistro(
    val id: String,
    val displayName: String,
    /**
     * Installed as the last step of setup. The environment is not considered
     * ready until these are in, so an interrupted install can never present
     * itself as usable.
     */
    val basePackages: List<String>,
    /** Installed on demand from the Settings card's "Install Packages" action. */
    val optionalPackages: List<String>,
    val packageManager: PackageManagerSpec,
) {
    /**
     * Base set is Kai Build's proven list: `tar` because OpenCode's installer
     * extracts a `.tar.gz`, `coreutils` because Claude's checks a `sha256sum`.
     */
    DEBIAN(
        id = "debian",
        displayName = "Debian 12",
        basePackages = listOf(
            "bash", "ca-certificates", "curl", "wget", "git",
            "nano", "less", "unzip", "python3", "tar", "coreutils",
        ),
        optionalPackages = listOf(
            "jq",
            "nodejs",
            "npm",
            "python3-pip",
            "openssh-client",
            "lftp",
            "rsync",
        ),
        packageManager = AptPackageManager,
    ),

    /**
     * `bash` is infrastructure rather than convenience: every persistent shell
     * session execs it directly, so the minirootfs is unusable without it.
     */
    ALPINE(
        id = "alpine",
        displayName = "Alpine Linux",
        basePackages = listOf("bash"),
        optionalPackages = listOf(
            "curl", "wget", "git", "jq", "python3", "py3-pip", "nodejs",
            "openssh-client", "lftp", "rsync",
        ),
        packageManager = ApkPackageManager,
    ),
    ;

    /**
     * Packages the Packages tab must never offer to uninstall — removing any of
     * them breaks the shell sessions the sandbox itself runs on.
     */
    val protectedPackages: Set<String> = basePackages.toSet()

    companion object {
        /**
         * What a fresh install becomes unless the user picks otherwise, and what
         * Kai Build always uses.
         */
        val DEFAULT = DEBIAN

        /**
         * Installs made before the distro was recorded are Alpine — that was the
         * only thing the chat sandbox could be.
         */
        val LEGACY = ALPINE

        fun fromId(id: String?): LinuxDistro = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
