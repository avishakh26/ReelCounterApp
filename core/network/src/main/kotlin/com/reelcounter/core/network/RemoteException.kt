package com.reelcounter.core.network

import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Every failure from the network layer, translated so callers never handle Supabase or Ktor
 * exception types. [code] is the server's machine-readable code when there is one: an auth
 * error code such as "invalid_credentials", or a Postgres SQLSTATE such as "23505".
 */
class RemoteException(
    val kind: Kind,
    val code: String? = null,
    val details: List<String> = emptyList(),
    message: String? = null,
    cause: Throwable? = null,
) : Exception(message ?: "$kind${code?.let { ": $it" }.orEmpty()}", cause) {

    enum class Kind {
        /** No connection, DNS failure, connection reset. */
        Network,
        Timeout,

        /** Rejected by Supabase Auth; see [code]. */
        Auth,

        /** Rejected by Postgres or PostgREST; [code] is the SQLSTATE or PostgREST code. */
        Database,

        /** Any other HTTP error from Supabase (e.g. Storage). */
        Server,
        Unknown,
    }
}

/** Runs [block], translating failures into [RemoteException]. Cancellation passes through. */
internal suspend fun <T> remoteCall(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: RemoteException) {
    throw e
} catch (e: AuthWeakPasswordException) {
    throw RemoteException(RemoteException.Kind.Auth, "weak_password", details = e.reasons, cause = e)
} catch (e: AuthRestException) {
    throw RemoteException(RemoteException.Kind.Auth, e.error, cause = e)
} catch (e: PostgrestRestException) {
    throw RemoteException(RemoteException.Kind.Database, e.code, cause = e)
} catch (e: RestException) {
    throw RemoteException(RemoteException.Kind.Server, e.error, cause = e)
} catch (e: HttpRequestTimeoutException) {
    throw RemoteException(RemoteException.Kind.Timeout, cause = e)
} catch (e: HttpRequestException) {
    throw RemoteException(RemoteException.Kind.Network, cause = e)
} catch (e: IOException) {
    throw RemoteException(RemoteException.Kind.Network, cause = e)
} catch (e: Exception) {
    throw RemoteException(RemoteException.Kind.Unknown, cause = e)
}
