package com.reelcounter.core.domain.auth

// Client-side checks for instant feedback. The server stays authoritative; these limits must
// match supabase/config.toml (minimum_password_length, otp_length).

enum class EmailIssue { Blank, Invalid }

object EmailRules {
    /** Practical check, not RFC 5322: one "@", a dotted domain, no spaces, at most 254 chars. */
    private val pattern = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]{2,}$""")

    fun normalize(input: String): String = input.trim()

    fun validate(email: String): EmailIssue? = when {
        email.isBlank() -> EmailIssue.Blank
        email.length > 254 || !pattern.matches(email) -> EmailIssue.Invalid
        else -> null
    }
}

enum class PasswordIssue { TooShort, TooLong }

/**
 * Length is what matters (NIST SP 800-63B), so there are no composition rules. The server
 * additionally rejects passwords it considers weak. The upper limit is bcrypt's 72-byte input.
 */
object PasswordRules {
    const val MIN_LENGTH = 8
    const val MAX_BYTES = 72

    fun validate(password: String): PasswordIssue? = when {
        password.length < MIN_LENGTH -> PasswordIssue.TooShort
        password.encodeToByteArray().size > MAX_BYTES -> PasswordIssue.TooLong
        else -> null
    }
}

object OtpRules {
    const val LENGTH = 6

    /** Keeps only digits, capped at [LENGTH], so pasted codes like "123 456" still work. */
    fun normalize(input: String): String = input.filter(Char::isDigit).take(LENGTH)

    fun isComplete(code: String): Boolean = code.length == LENGTH && code.all(Char::isDigit)
}
