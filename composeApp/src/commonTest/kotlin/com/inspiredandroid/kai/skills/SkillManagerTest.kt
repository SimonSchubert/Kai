package com.inspiredandroid.kai.skills

import com.inspiredandroid.kai.testutil.FakeSandboxController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SkillManagerTest {

    private fun skillMd(name: String, desc: String = "desc", body: String = "Do the thing.") = "---\nname: $name\ndescription: $desc\n---\n$body\n"

    private fun manager(sandbox: FakeSandboxController) = SkillManager(sandbox, backgroundDispatcher = UnconfinedTestDispatcher())

    @Test
    fun `load reads installed skill folders with their files`() = runTest {
        val sandbox = FakeSandboxController()
        sandbox.files["/root/skills/foo/SKILL.md"] = skillMd("foo", body = "Body of foo.")
        sandbox.files["/root/skills/foo/helper.py"] = "print('hi')"
        val mgr = manager(sandbox)

        mgr.load()

        // The built-in `create-skill` ships in compose resources and is always loaded too;
        // filter it out so this test asserts only on what landed from the sandbox.
        val sandboxSkills = mgr.getInstalled().filterNot { it.isBuiltIn }
        assertEquals(1, sandboxSkills.size)
        assertEquals("foo", sandboxSkills[0].id)
        assertEquals("Body of foo.", sandboxSkills[0].body.trim())
        assertEquals(listOf("helper.py"), sandboxSkills[0].bundledFilePaths)
    }

    @Test
    fun `load ignores folders without a SKILL_md`() = runTest {
        val sandbox = FakeSandboxController()
        sandbox.files["/root/skills/nope/readme.md"] = "no skill here"
        val mgr = manager(sandbox)

        mgr.load()

        assertTrue(mgr.getInstalled().none { !it.isBuiltIn })
    }

    @Test
    fun `install writes the folder and surfaces the skill`() = runTest {
        val sandbox = FakeSandboxController()
        val mgr = manager(sandbox)

        val result = mgr.install(
            DownloadedSkill(
                id = "bar",
                description = "desc",
                rawSkillMd = skillMd("bar"),
                files = mapOf("a.txt" to "x", "core/b.py" to "y"),
            ),
        )

        assertEquals("bar", result.id)
        assertEquals("x", sandbox.files["/root/skills/bar/a.txt"])
        assertEquals("y", sandbox.files["/root/skills/bar/core/b.py"])
        // Only top-level files are listed as bundled paths; nested dirs are not.
        assertEquals(listOf("a.txt"), mgr.getSkill("bar")?.bundledFilePaths)
    }

    @Test
    fun `reinstall replaces the previous folder contents`() = runTest {
        val sandbox = FakeSandboxController()
        val mgr = manager(sandbox)

        mgr.install(DownloadedSkill("bar", "desc", skillMd("bar"), mapOf("old.txt" to "1")))
        mgr.install(DownloadedSkill("bar", "desc", skillMd("bar"), mapOf("new.txt" to "2")))

        assertNull(sandbox.files["/root/skills/bar/old.txt"])
        assertEquals("2", sandbox.files["/root/skills/bar/new.txt"])
        assertEquals(listOf("new.txt"), mgr.getSkill("bar")?.bundledFilePaths)
    }

    @Test
    fun `uninstall deletes the folder`() = runTest {
        val sandbox = FakeSandboxController()
        val mgr = manager(sandbox)
        mgr.install(DownloadedSkill("bar", "desc", skillMd("bar"), mapOf("a.txt" to "x")))

        mgr.uninstall("bar")

        assertTrue(mgr.getInstalled().none { !it.isBuiltIn })
        assertTrue(sandbox.files.keys.none { it.startsWith("/root/skills/bar/") })
    }
}
