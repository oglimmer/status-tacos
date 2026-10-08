import Charts
import SwiftUI

/// Highest response time per time slot. Red areas are downtime.
struct ResponseTimeChart: View {
    let snapshot: PeriodSnapshot

    private struct Point: Identifiable {
        let date: Date
        let milliseconds: Int
        var id: Date { date }
    }

    private var points: [Point] {
        snapshot.dataPoints.compactMap { point in
            point.maxResponseTimeMs.map { Point(date: point.timestamp, milliseconds: $0) }
        }
    }

    /// Hours for 24 hours, days for longer windows. The default labels are too long.
    private var xLabelFormat: Date.FormatStyle {
        snapshot.end.timeIntervalSince(snapshot.start) <= 36 * 60 * 60
            ? .dateTime.hour()
            : .dateTime.day().month(.abbreviated)
    }

    var body: some View {
        let points = points
        let outages = DowntimeTimeline.merged(snapshot.downPeriods, start: snapshot.start, end: snapshot.end)

        if points.isEmpty && outages.isEmpty {
            Text("No response times in this time frame.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, minHeight: 120)
        } else {
            Chart {
                ForEach(outages.indices, id: \.self) { index in
                    RectangleMark(
                        xStart: .value("Down from", outages[index].0),
                        xEnd: .value("Down until", outages[index].1))
                    .foregroundStyle(.red.opacity(0.2))
                }
                ForEach(points) { point in
                    AreaMark(
                        x: .value("Time", point.date),
                        y: .value("Response time", point.milliseconds))
                    .interpolationMethod(.monotone)
                    .foregroundStyle(
                        .linearGradient(
                            colors: [Color.accentColor.opacity(0.35), Color.accentColor.opacity(0.02)],
                            startPoint: .top, endPoint: .bottom))
                    LineMark(
                        x: .value("Time", point.date),
                        y: .value("Response time", point.milliseconds))
                    .interpolationMethod(.monotone)
                    .lineStyle(StrokeStyle(lineWidth: 1.5))
                    .foregroundStyle(Color.accentColor)
                }
            }
            .chartXScale(domain: snapshot.start...snapshot.end)
            .chartXAxis {
                AxisMarks(values: .automatic(desiredCount: 4)) { _ in
                    AxisGridLine()
                    AxisTick()
                    AxisValueLabel(format: xLabelFormat)
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
