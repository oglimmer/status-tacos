import Charts
import SwiftUI
import UIKit

/// Highest response time per time slot, a teal line on a faint teal area. Red bands over the full
/// height are downtime; short ones are widened so they stay visible.
struct ResponseTimeChart: View {
    let snapshot: PeriodSnapshot
    @Environment(AppSettings.self) private var settings

    private struct Point: Identifiable {
        let date: Date
        let milliseconds: Int
        var id: Date { date }
    }

    /// Brand red, translucent. Stronger in dark mode, where a faint red disappears on the dark card.
    private static let downtimeBand = Color(uiColor: UIColor { traits in
        UIColor(Brand.down).resolvedColor(with: traits).withAlphaComponent(traits.userInterfaceStyle == .dark ? 0.35 : 0.25)
    })

    private var points: [Point] {
        snapshot.dataPoints.compactMap { point in
            point.maxResponseTimeMs.map { Point(date: point.timestamp, milliseconds: $0) }
        }
    }

    /// Hours for 24 hours, days for longer windows. The default labels are too long.
    private var hourlyLabels: Bool {
        snapshot.end.timeIntervalSince(snapshot.start) <= 36 * 60 * 60
    }

    var body: some View {
        let points = points
        let dateFormats = settings.dateFormats
        let outages = DetailPresentation.chartBands(
            periods: snapshot.downPeriods, start: snapshot.start, end: snapshot.end)

        if points.isEmpty && outages.isEmpty {
            Text("No response times in this time frame.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, minHeight: 120)
        } else {
            Chart {
                ForEach(outages.indices, id: \.self) { index in
                    RectangleMark(
                        xStart: .value("Down from", outages[index].start),
                        xEnd: .value("Down until", outages[index].end))
                    .foregroundStyle(Self.downtimeBand)
                }
                ForEach(points) { point in
                    AreaMark(
                        x: .value("Time", point.date),
                        y: .value("Response time", point.milliseconds))
                    .interpolationMethod(.monotone)
                    .foregroundStyle(
                        .linearGradient(
                            colors: [Brand.teal.opacity(0.2), Brand.teal.opacity(0.08)],
                            startPoint: .top, endPoint: .bottom))
                    LineMark(
                        x: .value("Time", point.date),
                        y: .value("Response time", point.milliseconds))
                    .interpolationMethod(.monotone)
                    .lineStyle(StrokeStyle(lineWidth: 1.5))
                    .foregroundStyle(Brand.teal)
                }
            }
            .chartXScale(domain: snapshot.start...snapshot.end)
            .chartXAxis {
                AxisMarks(values: .automatic(desiredCount: 4)) { value in
                    AxisGridLine()
                    AxisTick()
                    AxisValueLabel {
                        if let date = value.as(Date.self) {
                            Text(dateFormats.chartLabel(date, hourly: hourlyLabels))
                        }
                    }
                }
            }
            .chartYAxis {
                AxisMarks(position: .leading) { value in
                    AxisGridLine()
                    AxisValueLabel {
                        if let milliseconds = value.as(Int.self) {
                            Text(Format.responseTime(milliseconds))
                        }
                    }
                }
            }
            .frame(height: 200)
            .accessibilityLabel("Response time chart")
        }
    }
}
