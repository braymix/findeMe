package com.uwbcompass.app.net

import com.uwbcompass.core.Technology
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Kotlin representation of the wire protocol (see /shared/protocol.md). Kept in sync with
 * the JSON Schema in /shared/schemas. `v` is the protocol major version.
 */
const val PROTOCOL_MAJOR = 1
const val PROTOCOL_VERSION = "1.0.0"

@Serializable
data class Capabilities(val uwb: Boolean, val ble: Boolean, val gps: Boolean)

@Serializable
data class RangingPayload(val technology: Technology, val blob: String)

// ---- Outbound (client -> server) ----
// The `type` field on the wire is emitted by kotlinx.serialization from @SerialName via the
// class discriminator (configured as "type" in RendezvousClient); it must NOT also be a
// declared property or serialization throws a discriminator-collision error.
@Serializable
sealed interface ClientMessage {
    val v: Int
}

@Serializable
@SerialName("presence.hello")
data class PresenceHello(
    override val v: Int = PROTOCOL_MAJOR,
    val protocolVersion: String = PROTOCOL_VERSION,
    val platform: String = "android",
    val capabilities: Capabilities,
) : ClientMessage

@Serializable
@SerialName("presence.heartbeat")
data class PresenceHeartbeat(
    override val v: Int = PROTOCOL_MAJOR,
) : ClientMessage

@Serializable
@SerialName("session.invite")
data class SessionInvite(
    override val v: Int = PROTOCOL_MAJOR,
    val peerId: String,
) : ClientMessage

@Serializable
@SerialName("session.accept")
data class SessionAccept(
    override val v: Int = PROTOCOL_MAJOR,
    val sessionId: String,
) : ClientMessage

@Serializable
@SerialName("session.decline")
data class SessionDecline(
    override val v: Int = PROTOCOL_MAJOR,
    val sessionId: String,
) : ClientMessage

@Serializable
@SerialName("session.end")
data class SessionEnd(
    override val v: Int = PROTOCOL_MAJOR,
    val sessionId: String,
) : ClientMessage

@Serializable
@SerialName("ranging.ready")
data class RangingReady(
    override val v: Int = PROTOCOL_MAJOR,
    val sessionId: String,
    val rangingPayload: RangingPayload,
) : ClientMessage

@Serializable
@SerialName("technology.report")
data class TechnologyReport(
    override val v: Int = PROTOCOL_MAJOR,
    val sessionId: String,
    val technology: Technology,
    val reason: String,
) : ClientMessage

// ---- Inbound (server -> client) ----
// Parsed loosely via the raw JSON element to tolerate additive fields (protocol rule).
sealed interface ServerEvent {
    data class PresenceUpdate(val peerId: String, val online: Boolean) : ServerEvent
    data class SessionState(val sessionId: String, val state: String) : ServerEvent
    data class SessionIncoming(val sessionId: String, val fromId: String, val fromUsername: String) : ServerEvent
    data class Negotiated(val sessionId: String, val technology: Technology, val role: String) : ServerEvent
    data class PeerPayload(val sessionId: String, val payload: RangingPayload) : ServerEvent
    data class Downgrade(val sessionId: String, val technology: Technology) : ServerEvent
    data class Ended(val sessionId: String, val reason: String) : ServerEvent
    data class Error(val code: String, val message: String) : ServerEvent
    data class Unknown(val type: String, val raw: JsonElement) : ServerEvent
}
