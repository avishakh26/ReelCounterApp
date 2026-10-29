package com.reelcounter.core.domain.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CredentialRulesTest {

    @Test
    fun `valid emails pass, after trimming`() {
        assertNull(EmailRules.validate(EmailRules.normalize("  sam@example.com ")))
        assertNull(EmailRules.validate("first.last+tag@sub.example.co"))
    }

    @Test
    fun `blank and malformed emails are rejected`() {
        assertEquals(EmailIssue.Blank, EmailRules.validate(""))
        assertEquals(EmailIssue.Invalid, EmailRules.validate("sam"))
        assertEquals(EmailIssue.Invalid, EmailRules.validate("sam@example"))
        assertEquals(EmailIssue.Invalid, EmailRules.validate("sam @example.com"))
        assertEquals(EmailIssue.Invalid, EmailRules.validate("a".repeat(250) + "@x.com"))
    }

    @Test
    fun `password length is the only rule`() {
        assertEquals(PasswordIssue.TooShort, PasswordRules.validate("1234567"))
        assertNull(PasswordRules.validate("12345678"))
        assertNull(PasswordRules.validate("correct horse battery staple"))
    }

    @Test
    fun `password limit counts bytes, as bcrypt does`() {
        assertNull(PasswordRules.validate("a".repeat(72)))
        assertEquals(PasswordIssue.TooLong, PasswordRules.validate("a".repeat(73)))
        // 25 three-byte characters = 75 bytes, though only 25 chars.
        assertEquals(PasswordIssue.TooLong, PasswordRules.validate("€".repeat(25)))
    }

    @Test
    fun `otp input keeps digits only, capped at six`() {
        assertEquals("123456", OtpRules.normalize("123 456"))
        assertEquals("123456", OtpRules.normalize("1234567"))
        assertEquals("12", OtpRules.normalize("1a2"))
        assertTrue(OtpRules.isComplete("000000"))
        assertFalse(OtpRules.isComplete("12345"))
    }
}
