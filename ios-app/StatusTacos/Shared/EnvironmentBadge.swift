import SwiftUI

extension MonitorEnvironment {
    /// Color of the badge text. The background is the same color, faint.
    var color: Color {
        switch self {
        case .prod: Brand.environmentProd
        case .stage: Brand.environmentStage
        case .dev: Brand.environmentDev
        }
    }
}

/// Small tinted badge of a monitor environment: "PROD", "STAGE" or "DEV".
struct EnvironmentBadge: View {
    let environment: MonitorEnvironment
    /// Smaller text and padding, for the rows of the monitor list.
    var compact = false

    @ScaledMetric(relativeTo: .caption) private var horizontalPadding: CGFloat = 6
    @ScaledMetric(relativeTo: .caption) private var verticalPadding: CGFloat = 2
    @ScaledMetric(relativeTo: .caption) private var cornerRadius: CGFloat = 4
    @ScaledMetric(relativeTo: .caption2) private var compactHorizontalPadding: CGFloat = 5
    @ScaledMetric(relativeTo: .caption2) private var compactVerticalPadding: CGFloat = 1

    init(environment: MonitorEnvironment, compact: Bool = false) {
        self.environment = environment
        self.compact = compact
    }

    var body: some View {
        Text(environment.badgeText)
            .font((compact ? Font.caption2 : Font.caption).weight(.bold))
            .tracking(0.5)
            .foregroundStyle(environment.color)
            .lineLimit(1)
            .fixedSize()
            .padding(.horizontal, compact ? compactHorizontalPadding : horizontalPadding)
            .padding(.vertical, compact ? compactVerticalPadding : verticalPadding)
            .background(environment.color.opacity(0.14), in: .rect(cornerRadius: cornerRadius))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(environment.accessibilityName)
    }
}

#Preview {
    VStack(alignment: .leading, spacing: 12) {
        ForEach(MonitorEnvironment.allCases, id: \.self) { environment in
            HStack(spacing: 6) {
                EnvironmentBadge(environment: environment)
                Text("154 ms").font(.subheadline).foregroundStyle(.secondary)
            }
        }
    }
    .padding()
    .background(Color(.secondarySystemGroupedBackground))
}
