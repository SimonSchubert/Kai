package com.inspiredandroid.kai.linux

import java.io.File

/** Blocks path traversal: the resolved child must stay under [root]. */
internal fun safeChild(root: File, parts: List<String>): File? {
    val candidate = if (parts.isEmpty()) root else File(root, parts.joinToString(File.separator))
    val rootCanon = root.canonicalPath
    val candidateCanon = candidate.canonicalPath
    if (candidateCanon != rootCanon && !candidateCanon.startsWith(rootCanon + File.separator)) return null
    return candidate
}

/**
 * Translates a guest absolute path to the host file behind it, following the
 * same binds proot is started with. Both file browsers use this, so what the
 * user sees in the Files tab always matches what a shell in that environment sees.
 *
 * Branches are ordered most-specific first, which is what makes `/root/projects`
 * resolve to the bind rather than to the empty mount point inside the rootfs.
 */
class GuestFileMap(
    private val rootfsDir: File,
    /** Host directory bound to `/root`. */
    private val homeDir: File,
    /** Host directory bound to `/root/projects`, or null when nothing is bound there. */
    private val projectsDir: File?,
    /** Host directory bound to `/tmp`. */
    private val tmpDir: File,
) {

    fun resolve(guestPath: String): File? {
        val normalized = guestPath.trim().ifEmpty { "/" }
        if (!normalized.startsWith("/")) return null
        val parts = normalized.split("/").filter { it.isNotEmpty() }
        if (parts.any { it == ".." }) return null
        return when {
            projectsDir != null && parts.size >= 2 && parts[0] == "root" && parts[1] == "projects" ->
                safeChild(projectsDir, parts.drop(2))

            parts.firstOrNull() == "tmp" -> safeChild(tmpDir, parts.drop(1))

            parts.firstOrNull() == "root" -> safeChild(homeDir, parts.drop(1))

            else -> safeChild(rootfsDir, parts)
        }
    }

    /** The bind roots themselves are structure, not content: never renamed or deleted. */
    fun isRoot(file: File): Boolean {
        val canonical = file.canonicalPath
        return canonical == rootfsDir.canonicalPath ||
            canonical == homeDir.canonicalPath ||
            canonical == tmpDir.canonicalPath ||
            canonical == projectsDir?.canonicalPath
    }
}
