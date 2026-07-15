import Foundation

/// Backend endpoints. The shipped app talks to the Render deployment; a debug build can be
/// pointed at a local server by setting the `BACKEND_HTTP` / `BACKEND_WS` env vars in the
/// Xcode scheme.
enum BackendConfig {
    static var httpBaseURL: URL {
        if let override = ProcessInfo.processInfo.environment["BACKEND_HTTP"], let url = URL(string: override) {
            return url
        }
        return URL(string: "https://findeme.onrender.com")!
    }

    static var webSocketURL: URL {
        if let override = ProcessInfo.processInfo.environment["BACKEND_WS"], let url = URL(string: override) {
            return url
        }
        return URL(string: "wss://findeme.onrender.com/ws")!
    }
}
