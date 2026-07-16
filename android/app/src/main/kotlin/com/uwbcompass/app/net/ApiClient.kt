package com.uwbcompass.app.net

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
data class AuthUser(val id: String, val username: String, val email: String)

@Serializable
data class AuthTokens(val accessToken: String, val refreshToken: String, val user: AuthUser)

@Serializable
private data class RegisterReq(val username: String, val email: String, val password: String)

@Serializable
private data class LoginReq(val email: String, val password: String)

@Serializable
private data class AddContactReq(val username: String)

@Serializable
data class ContactDto(val id: String, val username: String, val online: Boolean)

@Serializable
private data class ApiError(val code: String? = null, val message: String? = null)

class ApiException(message: String) : Exception(message)

private val JSON_MEDIA = "application/json".toMediaType()

/**
 * Thin REST client for the backend auth + contacts endpoints. Uses generous timeouts so
 * the first request after a Render free-tier cold start (which can take ~30-60s) doesn't
 * fail. All calls are suspend and run off the main thread via OkHttp's async dispatcher.
 */
class ApiClient(
    private val baseUrl: String,
    private val http: OkHttpClient,
    private val json: Json,
) {
    /** Supplies the current access token for authenticated calls (null before login). */
    var tokenProvider: () -> String? = { null }

    suspend fun register(username: String, email: String, password: String): AuthTokens =
        postForTokens("/auth/register", json.encodeToString(RegisterReq.serializer(), RegisterReq(username, email, password)))

    suspend fun login(email: String, password: String): AuthTokens =
        postForTokens("/auth/login", json.encodeToString(LoginReq.serializer(), LoginReq(email, password)))

    suspend fun listContacts(): List<ContactDto> {
        val req = authed(Request.Builder().url("$baseUrl/contacts").get()).build()
        return http.newCall(req).await().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(errorMessage(text, resp.code))
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ContactDto.serializer()), text)
        }
    }

    suspend fun addContact(username: String): ContactDto {
        val body = json.encodeToString(AddContactReq.serializer(), AddContactReq(username)).toRequestBody(JSON_MEDIA)
        val req = authed(Request.Builder().url("$baseUrl/contacts").post(body)).build()
        return http.newCall(req).await().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(errorMessage(text, resp.code))
            json.decodeFromString(ContactDto.serializer(), text)
        }
    }

    private suspend fun postForTokens(path: String, body: String): AuthTokens {
        val req = Request.Builder().url("$baseUrl$path").post(body.toRequestBody(JSON_MEDIA)).build()
        return http.newCall(req).await().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException(errorMessage(text, resp.code))
            json.decodeFromString(AuthTokens.serializer(), text)
        }
    }

    private fun authed(builder: Request.Builder): Request.Builder {
        tokenProvider()?.let { builder.header("Authorization", "Bearer $it") }
        return builder
    }

    private fun errorMessage(text: String, code: Int): String =
        runCatching { json.decodeFromString(ApiError.serializer(), text).message }.getOrNull()
            ?: "Request failed ($code)"
}

/** Bridge OkHttp's async callback into a coroutine. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            cont.resume(response)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
