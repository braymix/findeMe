import Foundation

/// Client-side mirror of the backend session state machine (ADR-0007).
public enum SessionState: Sendable {
    case idle, invited, incoming, accepted, negotiated, active, ended, declined, expired, failed
}

public struct IllegalTransition: Error { public let from: SessionState; public let to: SessionState }

/// Guards client-side session transitions against the legal graph.
public final class SessionStateMachine {
    public private(set) var state: SessionState

    public init(initial: SessionState = .idle) { self.state = initial }

    private static let allowed: [String: Set<String>] = [
        key(.idle): [key(.invited), key(.incoming)],
        key(.invited): [key(.accepted), key(.declined), key(.expired), key(.failed), key(.ended)],
        key(.incoming): [key(.accepted), key(.declined), key(.expired), key(.failed), key(.ended)],
        key(.accepted): [key(.negotiated), key(.expired), key(.failed), key(.ended)],
        key(.negotiated): [key(.active), key(.expired), key(.failed), key(.ended)],
        key(.active): [key(.ended), key(.failed)],
        key(.ended): [], key(.declined): [], key(.expired): [], key(.failed): [],
    ]

    public var isTerminal: Bool {
        switch state {
        case .ended, .declined, .expired, .failed: return true
        default: return false
        }
    }

    public func canTransition(to: SessionState) -> Bool {
        (Self.allowed[Self.key(state)] ?? []).contains(Self.key(to))
    }

    @discardableResult
    public func transition(to: SessionState) throws -> SessionState {
        guard canTransition(to: to) else { throw IllegalTransition(from: state, to: to) }
        state = to
        return state
    }

    /// Returns to `.idle` from any terminal state (back to the peer list).
    public func resetIfTerminal() {
        if isTerminal { state = .idle }
    }

    private static func key(_ s: SessionState) -> String { String(describing: s) }
}
