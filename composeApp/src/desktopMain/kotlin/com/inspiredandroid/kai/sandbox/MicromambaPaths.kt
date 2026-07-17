package com.inspiredandroid.kai.sandbox

import java.io.File

/**
 * Maps JVM `os.arch` values to the conda-forge/micromamba platform tag used
 * in their release download URLs. See https://mamba.readthedocs.io/en/latest/installation/micromamba-installation.html
 */
fun micromambaDownloadUrl(osArch: String = System.getProperty("os.arch")): String {
    val tag = when {
        osArch == "amd64" || osArch == "x86_64" -> "linux-64"
        osArch == "aarch64" || osArch == "arm64" -> "linux-aarch64"
        else -> "linux-64"
    }
    return "https://micro.mamba.pm/api/micromamba/$tag/latest"
}

/**
 * Layout of the desktop micromamba install under `~/.kai/linux-sandbox/micromamba/`.
 * Construction does no I/O — callers create directories explicitly when needed.
 */
class MicromambaLayout(baseDir: File) {
    val binaryFile: File = File(baseDir, "bin/micromamba")
    val rootPrefix: File = File(baseDir, "root")
}
