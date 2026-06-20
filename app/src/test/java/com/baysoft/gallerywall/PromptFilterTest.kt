package com.baysoft.gallerywall

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PromptFilterTest {

    @Test
    fun testExplicitBlockedWords() {
        assertTrue("Should block explicit word 'porn'", PromptFilter.containsInappropriateContent("some porn content"))
        assertTrue("Should block explicit word 'naked'", PromptFilter.containsInappropriateContent("naked person"))
        assertTrue("Should block explicit word 'gore'", PromptFilter.containsInappropriateContent("gore and blood"))
    }

    @Test
    fun testBlockedWordsWithSymbols() {
        assertTrue("Should block 'p.o.r.n'", PromptFilter.containsInappropriateContent("p.o.r.n content"))
        assertTrue("Should block 'n-u-d-e'", PromptFilter.containsInappropriateContent("feeling n-u-d-e today"))
        assertTrue("Should block 'g_o_r_e'", PromptFilter.containsInappropriateContent("too much g_o_r_e"))
    }

    @Test
    fun testBlockedWordsWithLeetspeak() {
        assertTrue("Should block 'p0rn'", PromptFilter.containsInappropriateContent("p0rn video"))
        assertTrue("Should block 'n3de'", PromptFilter.containsInappropriateContent("n3de images"))
        assertTrue("Should block 'bl00dy'", PromptFilter.containsInappropriateContent("bl00dy scene"))
    }

    @Test
    fun testSafeWords() {
        assertFalse("Should NOT block 'sunset'", PromptFilter.containsInappropriateContent("beautiful sunset over mountains"))
        assertFalse("Should NOT block 'kitten'", PromptFilter.containsInappropriateContent("fluffy kitten playing with yarn"))
        assertFalse("Should NOT block 'gorgeous' (word boundary check)", PromptFilter.containsInappropriateContent("a gorgeous landscape"))
        assertFalse("Should NOT block 'basement' (word boundary check)", PromptFilter.containsInappropriateContent("cleaning the basement"))
    }

    @Test
    fun testCaseInsensitivity() {
        assertTrue("Should block 'PORN'", PromptFilter.containsInappropriateContent("PORN CONTENT"))
        assertTrue("Should block 'NaKeD'", PromptFilter.containsInappropriateContent("NaKeD person"))
    }

    @Test
    fun testEmptyAndBlank() {
        assertFalse("Should NOT block empty string", PromptFilter.containsInappropriateContent(""))
        assertFalse("Should NOT block blank string", PromptFilter.containsInappropriateContent("   "))
    }
}
