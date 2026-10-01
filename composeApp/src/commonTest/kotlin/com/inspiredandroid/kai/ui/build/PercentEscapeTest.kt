package com.inspiredandroid.kai.ui.build

import kotlin.test.Test
import kotlin.test.assertEquals

/** The Kai Build download label must show `42%`, not the raw `%%` escape (#512). */
class PercentEscapeTest {

    @Test
    fun doubledPercentCollapsesToOne() {
        assertEquals("Downloading Debian… 42%", "Downloading Debian… 42%%".collapsePercentEscape())
    }

    @Test
    fun alreadyUnescapedLabelIsUnchanged() {
        assertEquals("Downloading Debian… 42%", "Downloading Debian… 42%".collapsePercentEscape())
    }
}
