package com.reelcounter.core.domain.profile

// Mirrors the CHECK constraints on public.profiles (supabase/migrations/*_identity_and_profiles.sql).
// Change both together.

enum class UsernameIssue { TooShort, TooLong, InvalidCharacters, MustStartOrEndWithLetterOrDigit, ConsecutivePeriods }

object UsernameRules {
    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 20

    private val allowed = Regex("^[a-z0-9_.]*$")
    private val edge = Regex("[a-z0-9]")

    /** Usernames are stored lowercase; input is trimmed and lowercased as the user types. */
    fun normalize(input: String): String = input.trim().lowercase()

    fun validate(username: String): UsernameIssue? = when {
        username.length < MIN_LENGTH -> UsernameIssue.TooShort
        username.length > MAX_LENGTH -> UsernameIssue.TooLong
        !allowed.matches(username) -> UsernameIssue.InvalidCharacters
        !edge.matches(username.first().toString()) || !edge.matches(username.last().toString()) ->
            UsernameIssue.MustStartOrEndWithLetterOrDigit
        ".." in username -> UsernameIssue.ConsecutivePeriods
        else -> null
    }
}

object ProfileTextRules {
    const val DISPLAY_NAME_MAX = 50
    const val BIO_MAX = 160

    private val whitespace = Regex("""\s+""")
    private val control = Regex("""\p{Cntrl}""")

    /** Trims and collapses runs of whitespace; blank becomes null. */
    fun normalizeDisplayName(input: String): String? =
        input.trim().replace(whitespace, " ").ifEmpty { null }

    /** Trims; blank becomes null. Line breaks inside a bio are kept. */
    fun normalizeBio(input: String): String? = input.trim().ifEmpty { null }

    fun isValidDisplayName(value: String?): Boolean =
        value == null || (value.length <= DISPLAY_NAME_MAX && !control.containsMatchIn(value))

    fun isValidBio(value: String?): Boolean = value == null || value.length <= BIO_MAX
}
