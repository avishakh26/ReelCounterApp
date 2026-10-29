package com.reelcounter.core.data.error

import com.reelcounter.core.domain.auth.AuthError
import com.reelcounter.core.domain.profile.ProfileError
import com.reelcounter.core.network.RemoteException
import com.reelcounter.core.network.RemoteException.Kind
import kotlin.test.Test
import kotlin.test.assertEquals

class RemoteErrorMappingTest {

    private fun auth(code: String?) = RemoteException(Kind.Auth, code).toAuthError()

    @Test
    fun `auth error codes map to domain errors`() {
        assertEquals(AuthError.InvalidCredentials, auth("invalid_credentials"))
        assertEquals(AuthError.EmailNotConfirmed, auth("email_not_confirmed"))
        assertEquals(AuthError.EmailAlreadyRegistered, auth("user_already_exists"))
        assertEquals(AuthError.EmailAlreadyRegistered, auth("email_exists"))
        assertEquals(AuthError.InvalidOrExpiredCode, auth("otp_expired"))
        assertEquals(AuthError.RateLimited, auth("over_email_send_rate_limit"))
        assertEquals(AuthError.SamePassword, auth("same_password"))
        assertEquals(AuthError.Unknown("something_new"), auth("something_new"))
    }

    @Test
    fun `weak password keeps the server's reasons`() {
        val error = RemoteException(Kind.Auth, "weak_password", details = listOf("pwned")).toAuthError()
        assertEquals(AuthError.WeakPassword(listOf("pwned")), error)
    }

    @Test
    fun `connectivity problems are network errors`() {
        assertEquals(AuthError.Network, RemoteException(Kind.Network).toAuthError())
        assertEquals(AuthError.Network, RemoteException(Kind.Timeout).toAuthError())
        assertEquals(ProfileError.Network, RemoteException(Kind.Timeout).toProfileError())
    }

    @Test
    fun `database codes from the migration map to profile errors`() {
        assertEquals(ProfileError.UsernameTaken, RemoteException(Kind.Database, "23505").toProfileError())
        assertEquals(ProfileError.UsernameReserved, RemoteException(Kind.Database, "RC001").toProfileError())
        assertEquals(ProfileError.Unknown("42501"), RemoteException(Kind.Database, "42501").toProfileError())
    }
}
