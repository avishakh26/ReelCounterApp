package com.reelcounter.core.domain.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** These cases mirror the username CHECK constraint tested in supabase/tests/pglite. */
class ProfileRulesTest {

    @Test
    fun `usernames are normalized to trimmed lowercase`() {
        assertEquals("sam.k", UsernameRules.normalize("  Sam.K "))
    }

    @Test
    fun `valid usernames pass`() {
        listOf("sam", "sam.k", "bob.the_builder", "a1b", "x".repeat(20)).forEach {
            assertNull(UsernameRules.validate(it), "expected '$it' to be valid")
        }
    }

    @Test
    fun `invalid usernames report the specific problem`() {
        assertEquals(UsernameIssue.TooShort, UsernameRules.validate("ab"))
        assertEquals(UsernameIssue.TooLong, UsernameRules.validate("x".repeat(21)))
        assertEquals(UsernameIssue.InvalidCharacters, UsernameRules.validate("sam-k"))
        assertEquals(UsernameIssue.InvalidCharacters, UsernameRules.validate("Sam"))
        assertEquals(UsernameIssue.MustStartOrEndWithLetterOrDigit, UsernameRules.validate("_sam"))
        assertEquals(UsernameIssue.MustStartOrEndWithLetterOrDigit, UsernameRules.validate("sam."))
        assertEquals(UsernameIssue.ConsecutivePeriods, UsernameRules.validate("sam..k"))
    }

    @Test
    fun `display names are trimmed with whitespace collapsed, and blank becomes null`() {
        assertEquals("Sam Kim", ProfileTextRules.normalizeDisplayName("  Sam   Kim "))
        assertNull(ProfileTextRules.normalizeDisplayName("   "))
    }

    @Test
    fun `display name and bio limits match the database`() {
        assertTrue(ProfileTextRules.isValidDisplayName("a".repeat(50)))
        assertFalse(ProfileTextRules.isValidDisplayName("a".repeat(51)))
        assertFalse(ProfileTextRules.isValidDisplayName("tab\there"))
        assertTrue(ProfileTextRules.isValidBio("b".repeat(160)))
        assertFalse(ProfileTextRules.isValidBio("b".repeat(161)))
        assertTrue(ProfileTextRules.isValidBio(null))
    }

    @Test
    fun `bio keeps inner line breaks but drops surrounding blank space`() {
        assertEquals("line one\nline two", ProfileTextRules.normalizeBio("\n line one\nline two \n"))
    }
}
