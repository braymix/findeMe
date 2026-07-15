package com.uwbcompass.app.net

import com.uwbcompass.core.Technology
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * WebSocket rendezvous client. Sends typed [ClientMessage]s, decodes server events into
 * [ServerEvent], and drives a 15s heartbeat. Reconnection/backoff is intentionally simple
 * (sessions are short); the ViewModel decides how to react to drops.
 */
class RendezvousClient(
    private val wsUrl: String,
    private val accessToken: String,
    private val scope: CoroutineScope,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "type" }
    private var socket: WebSocket? = null

    private val _events = MutableSharedFlow<ServerEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<ServerEvent> = _events.asSharedFlow()

    fun connect(capabilities: Capabilities) {
        val request = Request.Builder().url("$wsUrl?token=$accessToken").build()
        socket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                send(PresenceHello(capabilities = capabilities))
                startHeartbeat()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                decode(text)?.let { scope.launch { _events.emit(it) } }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                scope.launch { _events.emit(ServerEvent.Error("WS_FAILURE", t.message ?: "socket failure")) }
            }
        })
    }

    private fun startHeartbeat() {
        scope.launch(Dispatchers.IO) {
            while (isActive && socket != null) {
                delay(15_000)
                send(PresenceHeartbeat())
            }
        }
    }

    fun send(msg: ClientMessage) {
        socket?.send(json.encodeToString(ClientMessage.serializer(), msg))
    }

    fun invite(peerId: String) = send(SessionInvite(peerId = peerId))
    fun accept(sessionId: String) = send(SessionAccept(sessionId = sessionId))
    fun decline(sessionId: String) = send(SessionDecline(sessionId = sessionId))
    fun end(sessionId: String) = send(SessionEnd(sessionId = sessionId))
    fun rangingReady(sessionId: String, payload: RangingPayload) =
        send(RangingReady(sessionId = sessionId, rangingPayload = payload))

    fun reportDowngrade(sessionId: String, technology: Technology, reason: String) =
        send(TechnologyReport(sessionId = sessionId, technology = technology, reason = reason))

    fun close() {
        socket?.close(1000, "client closed")
        socket = null
    }

    /** Loose decode so additive server fields don't break older clients (protocol rule). */
    private fun decode(text: String): ServerEvent? {
        val obj: JsonObject = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val type = obj["type"]?.jsonPrimitive?.contentOrNull ?: return null
        fun str(k: String) = obj[k]?.jsonPrimitive?.contentOrNull ?: ""
        fun tech(k: String) = Technology.valueOf(str(k))
        return when (type) {
            "presence.update" -> ServerEvent.PresenceUpdate(str("peerId"), obj["online"]?.jsonPrimitive?.content == "true")
            "session.state" -> ServerEvent.SessionState(str("sessionId"), str("state"))
            "session.incoming" -> {
                val from = obj["from"]?.jsonObject
                ServerEvent.SessionIncoming(
                    str("sessionId"),
                    from?.get("id")?.jsonPrimitive?.contentOrNull ?: "",
                    from?.get("username")?.jsonPrimitive?.contentOrNull ?: "",
                )
            }
            "session.negotiated" -> ServerEvent.Negotiated(str("sessionId"), tech("technology"), str("role"))
            "ranging.peerPayload" -> {
                val p = obj["rangingPayload"]!!.jsonObject
                ServerEvent.PeerPayload(
                    str("sessionId"),
                    RangingPayload(Technology.valueOf(p["technology"]!!.jsonPrimitive.content), p["blob"]!!.jsonPrimitive.content),
                )
            }
            "technology.downgrade" -> ServerEvent.Downgrade(str("sessionId"), tech("technology"))
            "session.ended" -> ServerEvent.Ended(str("sessionId"), str("reason"))
            "error" -> ServerEvent.Error(str("code"), str("message"))
            else -> ServerEvent.Unknown(type, obj)
        }
    }
}
