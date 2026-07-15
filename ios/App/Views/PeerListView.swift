import SwiftUI

struct Peer: Identifiable, Equatable {
    let id: String
    let username: String
    var online: Bool
}

/// Contact list with live presence; tap a peer to send a find request. Includes an
/// incoming-invite consent alert (explicit, voluntary — privacy by design).
struct PeerListView: View {
    let peers: [Peer]
    let incoming: (sessionId: String, fromUsername: String)?
    let onSelect: (Peer) -> Void
    let onAddContact: () -> Void
    let onAcceptIncoming: () -> Void
    let onDeclineIncoming: () -> Void

    var body: some View {
        NavigationStack {
            List(peers) { peer in
                Button { onSelect(peer) } label: {
                    HStack(spacing: 12) {
                        Circle().fill(peer.online ? .green : .gray).frame(width: 10, height: 10)
                        Text(peer.username)
                        Spacer()
                        if peer.online { Image(systemName: "location.north.line") }
                    }
                }
                .disabled(!peer.online)
            }
            .navigationTitle("Peers")
            .toolbar {
                Button(action: onAddContact) { Image(systemName: "person.badge.plus") }
            }
            .alert("Find request", isPresented: .constant(incoming != nil)) {
                Button("Accept", action: onAcceptIncoming)
                Button("Decline", role: .cancel, action: onDeclineIncoming)
            } message: {
                Text("\(incoming?.fromUsername ?? "Someone") wants to locate you. Share your direction & distance with them?")
            }
        }
    }
}
