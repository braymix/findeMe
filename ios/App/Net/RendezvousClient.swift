import Foundation
import UWBCompassCore

// Kotlin/JSON wire protocol mirror (see /shared/protocol.md). `v` is the protocol major.
let protocolMajor = 1
let protocolVersion = "1.0.0"

struct Capabilities: Codable { let uwb: Bool; let ble: Bool; let gps: Bool }
struct WireRangingPayload: Codable { let technology: Technology; let blob: String }

/// Server → client events, decoded loosely to tolerate additive fields (protocol rule).
enum ServerEvent {
    case presenceUpdate(peerId: String, online: Bool)
    case sessionState(sessionId: String, state: String)
    case incoming(sessionId: String, fromId: String, fromUsername: String)
    case negotiated(sessionId: String, technology: Technology, role: String)
    case peerPayload(sessionId: String, payload: WireRangingPayload)
    case downgrade(sessionId: String, technology: Technology)
    case ended(sessionId: String, reason: String)
    case error(code: String, message: String)
    case unknown(type: String)
}

/// WebSocket rendezvous client backed by URLSessionWebSocketTask. Sends typed client
/// messages, decodes server events, and drives a 15 s heartbeat. Ranging measurements are
/// never sent here — only opaque discovery-token blobs (ADR-0002).
final class RendezvousClient: NSObject {
    private let wsURL: URL
    private let accessToken: String
    private var task: URLSessionWebSocketTask?
    private var heartbeat: Timer?
    var onEvent: ((ServerEvent) -> Void)?

    init(wsURL: URL, accessToken: String) {
        self.wsURL = wsURL
        self.accessToken = accessToken
    }

    func connect(capabilities: Capabilities) {
        var comps = URLComponents(url: wsURL, resolvingAgainstBaseURL: false)!
        comps.queryItems = [URLQueryItem(name: "token", value: accessToken)]
        let session = URLSession(configuration: .default)
        let task = session.webSocketTask(with: comps.url!)
        self.task = task
        task.resume()
        sendHello(capabilities)
        receiveLoop()
        heartbeat = Timer.scheduledTimer(withTimeInterval: 15, repeats: true) { [weak self] _ in
            self?.sendRaw(["v": protocolMajor, "type": "presence.heartbeat"])
        }
    }

    private func sendHello(_ caps: Capabilities) {
        sendRaw([
            "v": protocolMajor, "type": "presence.hello",
            "protocolVersion": protocolVersion, "platform": "ios",
            "capabilities": ["uwb": caps.uwb, "ble": caps.ble, "gps": caps.gps],
        ])
    }

    func invite(peerId: String) { sendRaw(["v": protocolMajor, "type": "session.invite", "peerId": peerId]) }
    func accept(_ id: String) { sendRaw(["v": protocolMajor, "type": "session.accept", "sessionId": id]) }
    func decline(_ id: String) { sendRaw(["v": protocolMajor, "type": "session.decline", "sessionId": id]) }
    func end(_ id: String) { sendRaw(["v": protocolMajor, "type": "session.end", "sessionId": id]) }

    func rangingReady(sessionId: String, payload: WireRangingPayload) {
        sendRaw([
            "v": protocolMajor, "type": "ranging.ready", "sessionId": sessionId,
            "rangingPayload": ["technology": payload.technology.rawValue, "blob": payload.blob],
        ])
    }

    func reportDowngrade(sessionId: String, technology: Technology, reason: String) {
        sendRaw([
            "v": protocolMajor, "type": "technology.report", "sessionId": sessionId,
            "technology": technology.rawValue, "reason": reason,
        ])
    }

    func close() {
        heartbeat?.invalidate()
        task?.cancel(with: .goingAway, reason: nil)
    }

    private func sendRaw(_ dict: [String: Any]) {
        guard let data = try? JSONSerialization.data(withJSONObject: dict),
              let text = String(data: data, encoding: .utf8) else { return }
        task?.send(.string(text)) { _ in }
    }

    private func receiveLoop() {
        task?.receive { [weak self] result in
            guard let self else { return }
            if case let .success(message) = result {
                if case let .string(text) = message { self.decode(text) }
                self.receiveLoop()
            }
        }
    }

    private func decode(_ text: String) {
        guard let data = text.data(using: .utf8),
              let obj = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any],
              let type = obj["type"] as? String else { return }
        func tech(_ k: String) -> Technology { Technology(rawValue: obj[k] as? String ?? "") ?? .BLE }
        let event: ServerEvent
        switch type {
        case "presence.update":
            event = .presenceUpdate(peerId: obj["peerId"] as? String ?? "", online: obj["online"] as? Bool ?? false)
        case "session.state":
            event = .sessionState(sessionId: obj["sessionId"] as? String ?? "", state: obj["state"] as? String ?? "")
        case "session.incoming":
            let from = obj["from"] as? [String: Any]
            event = .incoming(sessionId: obj["sessionId"] as? String ?? "",
                              fromId: from?["id"] as? String ?? "",
                              fromUsername: from?["username"] as? String ?? "")
        case "session.negotiated":
            event = .negotiated(sessionId: obj["sessionId"] as? String ?? "",
                                technology: tech("technology"), role: obj["role"] as? String ?? "")
        case "ranging.peerPayload":
            let p = obj["rangingPayload"] as? [String: Any] ?? [:]
            event = .peerPayload(sessionId: obj["sessionId"] as? String ?? "",
                                 payload: WireRangingPayload(technology: Technology(rawValue: p["technology"] as? String ?? "") ?? .UWB,
                                                             blob: p["blob"] as? String ?? ""))
        case "technology.downgrade":
            event = .downgrade(sessionId: obj["sessionId"] as? String ?? "", technology: tech("technology"))
        case "session.ended":
            event = .ended(sessionId: obj["sessionId"] as? String ?? "", reason: obj["reason"] as? String ?? "")
        case "error":
            event = .error(code: obj["code"] as? String ?? "", message: obj["message"] as? String ?? "")
        default:
            event = .unknown(type: type)
        }
        DispatchQueue.main.async { self.onEvent?(event) }
    }
}
