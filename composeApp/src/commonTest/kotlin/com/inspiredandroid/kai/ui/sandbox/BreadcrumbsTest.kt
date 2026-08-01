package com.inspiredandroid.kai.ui.sandbox

import kotlin.test.Test
import kotlin.test.assertEquals

class BreadcrumbsTest {

    @Test
    fun `filesystem root keeps the sandbox trail`() {
        assertEquals(
            listOf("/" to "/", "root" to "/root", "notes" to "/root/notes"),
            breadcrumbs(currentPath = "/root/notes", rootPath = "/"),
        )
    }

    @Test
    fun `project root is the first crumb`() {
        assertEquals(
            listOf("demo" to "/root/projects/demo", "src" to "/root/projects/demo/src"),
            breadcrumbs(currentPath = "/root/projects/demo/src", rootPath = "/root/projects/demo"),
        )
    }

    @Test
    fun `sitting on the root offers nothing above it`() {
        assertEquals(
            listOf("demo" to "/root/projects/demo"),
            breadcrumbs(currentPath = "/root/projects/demo", rootPath = "/root/projects/demo"),
        )
    }

    @Test
    fun `a path outside the root collapses to the root`() {
        assertEquals(
            listOf("demo" to "/root/projects/demo"),
            breadcrumbs(currentPath = "/etc", rootPath = "/root/projects/demo"),
        )
    }

    @Test
    fun `a trailing slash on the root does not add an empty crumb`() {
        assertEquals(
            listOf("demo" to "/root/projects/demo", "src" to "/root/projects/demo/src"),
            breadcrumbs(currentPath = "/root/projects/demo/src", rootPath = "/root/projects/demo/"),
        )
    }
}
