import SwiftUI

/// The demo strip of the website: 12 minutes of checks of one URL, one every 15 seconds,
/// with a one-minute outage. Drag across the bars to read each check.
struct CheckStrip: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var picked: Int?
    @State private var appeared = false

    private static let count = 48
    private static let outage = 30..<34
    // The default alert threshold is 30 seconds, so the alert goes out on the third failed check.
    private static let alertAt = 32

    private struct Check {
        let index: Int
        let down: Bool
        let milliseconds: Int

        var time: String {
            let seconds = 12 * 3600 + index * 15
            return String(format: "%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
        }

        var result: String { down ? "Down, status 503" : "Up, \(milliseconds) ms" }
        var height: Double { down ? 1 : Double(milliseconds) / 92 }
    }

    // Plausible but fixed response times, the same as on the website.
    private static let checks = (0..<count).map { i in
        Check(index: i, down: outage.contains(i), milliseconds: 38 + (i * 37) % 23 + (i % 7 == 3 ? 31 : 0))
    }

    private var shown: Check { Self.checks[picked ?? Self.count - 1] }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Side by side when it fits, else the readout goes under the URL (large text).
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .firstTextBaseline) {
                    url
                    Spacer(minLength: 8)
                    readout
                }
                VStack(alignment: .leading, spacing: 4) {
                    url
                    readout
                }
            }

            bars
                .frame(height: 64)
                .overlay(alignment: .bottom) {
                    Rectangle().fill(Brand.line).frame(height: 2)
                }

            VStack(alignment: .leading, spacing: 3) {
                event(at: Self.outage.lowerBound, "Status 503. The check fails.")
                event(at: Self.alertAt, "Down for 30 seconds. Alert sent.")
                event(at: Self.outage.upperBound, "Answers again. All-clear sent.")
            }
            .font(.footnote)
        }
        .foregroundStyle(Brand.ink)
        .padding(16)
        .background(Brand.paper, in: .rect(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Brand.line, lineWidth: 2))
        .onAppear { appeared = true }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Example: twelve minutes of checks of one URL")
        .accessibilityValue("\(shown.time), \(shown.result)")
        .accessibilityHint("Swipe up or down to read each check.")
        .accessibilityAdjustableAction { direction in
            let current = picked ?? Self.count - 1
            picked = min(Self.count - 1, max(0, current + (direction == .increment ? 1 : -1)))
        }
    }

    private var url: some View {
        Text("example.com/health")
            .font(.subheadline.weight(.semibold))
            .lineLimit(1)
            .fixedSize()
    }

    private var readout: some View {
        HStack(spacing: 6) {
            Circle().frame(width: 8, height: 8)
            Text(shown.result).font(.subheadline.weight(.semibold))
        }
        .foregroundStyle(shown.down ? Brand.down : Brand.up)
        .monospacedDigit()
        .fixedSize()
    }

    private var bars: some View {
        GeometryReader { geometry in
            HStack(alignment: .bottom, spacing: 2) {
                ForEach(Self.checks, id: \.index) { check in
                    UnevenRoundedRectangle(topLeadingRadius: 1.5, topTrailingRadius: 1.5)
                        .fill(check.down ? Brand.down : Brand.up)
                        .frame(height: geometry.size.height * max(0.3, check.height))
                        .opacity(picked == nil || picked == check.index ? 1 : 0.45)
                        .scaleEffect(y: appeared || reduceMotion ? 1 : 0, anchor: .bottom)
                        .animation(
                            reduceMotion ? nil : .spring(duration: 0.3, bounce: 0.3).delay(Double(check.index) * 0.03),
                            value: appeared
                        )
                }
            }
            .frame(maxHeight: .infinity, alignment: .bottom)
            .contentShape(.rect)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let i = Int(value.location.x / geometry.size.width * Double(Self.count))
                        picked = min(Self.count - 1, max(0, i))
                    }
                    .onEnded { _ in picked = nil }
            )
            .sensoryFeedback(.selection, trigger: picked) { _, new in new != nil }
        }
    }

    private func event(at index: Int, _ text: String) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            Text(Self.checks[index].time).fontWeight(.bold).monospacedDigit()
            Text(text)
        }
    }
}

#Preview {
    CheckStrip().padding()
}
