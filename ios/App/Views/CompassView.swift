import SwiftUI
import UWBCompassCore

/// Adaptive compass (requirement 4): a precise rotating arrow for UWB with a directional
/// fix, or a hot/cold proximity ring for BLE/GPS — never a fake arrow. Always shows
/// distance, a technology badge, and a signal-quality badge.
struct CompassView: View {
    @ObservedObject var model: CompassViewModel
    let peerName: String
    let onEnd: () -> Void

    var body: some View {
        VStack {
            VStack(spacing: 8) {
                Text(peerName).font(.title2)
                HStack(spacing: 8) {
                    badge(model.technology.rawValue)
                    badge(qualityLabel(model.quality))
                }
            }
            Spacer()
            ZStack {
                if model.hasDirection, let deg = model.arrowDeg {
                    ArrowShape()
                        .fill(Color.accentColor)
                        .frame(width: 200, height: 200)
                        .rotationEffect(.degrees(deg))
                        .animation(.easeOut(duration: 0.1), value: deg)
                } else {
                    ProximityRing(quality: model.quality)
                        .frame(width: 220, height: 220)
                }
            }
            Spacer()
            VStack(spacing: 4) {
                Text(model.distanceMeters.map { String(format: "%.1f m", $0) } ?? "—")
                    .font(.system(size: 48, weight: .bold))
                Text(accuracyHint(model.technology, model.quality)).font(.caption)
            }
            Button(role: .destructive, action: onEnd) {
                Text("End session").frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .padding(.top)
        }
        .padding(24)
    }

    private func badge(_ text: String) -> some View {
        Text(text).padding(.horizontal, 12).padding(.vertical, 4)
            .background(Capsule().fill(Color.secondary.opacity(0.2)))
    }

    private func qualityLabel(_ q: SignalQuality) -> String {
        switch q {
        case .high: return "Signal: high"
        case .medium: return "Signal: medium"
        case .low: return "Signal: low"
        case .lost: return "Signal: lost"
        }
    }

    private func accuracyHint(_ tech: Technology, _ q: SignalQuality) -> String {
        switch tech {
        case .UWB: return q == .high ? "±10 cm · precise direction" : "UWB · direction may be coarse"
        case .BLE: return "approximate proximity (no direction)"
        case .GPS: return "coarse outdoor estimate"
        }
    }
}

private struct ArrowShape: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path()
        let w = rect.width, h = rect.height
        p.move(to: CGPoint(x: w * 0.5, y: 0))
        p.addLine(to: CGPoint(x: w * 0.18, y: h * 0.72))
        p.addLine(to: CGPoint(x: w * 0.5, y: h * 0.58))
        p.addLine(to: CGPoint(x: w * 0.82, y: h * 0.72))
        p.closeSubpath()
        return p
    }
}

private struct ProximityRing: View {
    let quality: SignalQuality
    var body: some View {
        let (color, stroke): (Color, CGFloat) = {
            switch quality {
            case .high: return (.red, 40)     // hot / near
            case .medium: return (.orange, 28)
            case .low: return (.blue, 16)     // cold / far
            case .lost: return (.gray, 8)
            }
        }()
        return Circle().stroke(color, lineWidth: stroke)
    }
}
