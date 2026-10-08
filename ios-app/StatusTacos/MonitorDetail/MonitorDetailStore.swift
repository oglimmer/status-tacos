import Foundation
import Observation

enum Timeframe: String, CaseIterable, Identifiable, Sendable {
    case day = "24h"
    case week = "7d"
    case quarter = "90d"

    var id: Self { self }
}

/// Uptime, response times and downtime of one monitor in one time window.
struct PeriodSnapshot: Sendable {
    let start: Date
    let end: Date
    let uptimePercentage: Double?
    let totalChecks: Int?
    let successfulChecks: Int?
    /// Only the 7 and 90 day stats have these.
    let responseTimes: ResponseTimeSummary?
    let dataPoints: [ResponseTimeDataPoint]
    let downPeriods: [DownPeriod]

    struct ResponseTimeSummary: Sendable, Equatable {
        let average: Int?
        let p99: Int?
        let minimum: Int?
        let maximum: Int?
    }

    /// The 24h history has no window: it ends at the time of the request.
    init(history: ResponseTimeHistoryDTO, fetchedAt: Date) {
        end = fetchedAt
        start = fetchedAt.addingTimeInterval(-24 * 60 * 60)
        uptimePercentage = history.uptimePercentage24h
        totalChecks = history.totalChecks24h
        successfulChecks = history.successfulChecks24h
        responseTimes = nil
        dataPoints = history.dataPoints ?? []
        downPeriods = history.statusDownPeriods ?? []
    }

    init?(stats: UptimeStatsDTO) {
        guard let start = stats.periodStart, let end = stats.periodEnd, end > start else { return nil }
        self.start = start
        self.end = end
        uptimePercentage = stats.uptimePercentage
        totalChecks = stats.totalChecks
        successfulChecks = stats.successfulChecks
        responseTimes = ResponseTimeSummary(
            average: stats.avgResponseTimeMs, p99: stats.p99ResponseTimeMs,
            minimum: stats.minResponseTimeMs, maximum: stats.maxResponseTimeMs)
        dataPoints = stats.responseTimeDataPoints ?? []
        downPeriods = stats.statusDownPeriods ?? []
    }

    var totalDowntime: TimeInterval {
        DowntimeTimeline.totalDowntime(periods: downPeriods, start: start, end: end)
    }

    /// Outages in the window, newest first. Overlapping periods are merged.
    var outages: [DownPeriod] {
        DowntimeTimeline.merged(downPeriods, start: start, end: end)
            .map { DownPeriod(start: $0.0, end: $0.1) }
            .reversed()
    }
}

@MainActor
@Observable
final class MonitorDetailStore {
    let monitorID: Int
    private(set) var status: MonitorStatusDTO?
    private(set) var snapshots: [Timeframe: PeriodSnapshot] = [:]
    private(set) var isLoading = false
    private(set) var hasLoaded = false
    private(set) var errorMessage: String?

    @ObservationIgnored private let api: APIClient

    init(monitorID: Int, status: MonitorStatusDTO?, api: APIClient) {
        self.monitorID = monitorID
        self.status = status
        self.api = api
    }

    func refresh() async {
        guard !isLoading else { return }
        isLoading = true
        defer { isLoading = false }

        do {
            async let history = api.responseTimeHistory24h(monitorID: monitorID)
            async let stats = api.uptimeStats(monitorID: monitorID)
            // Keeps "last check" and the current status fresh. Not needed for the charts.
            async let statuses = try? api.monitorStatuses()

            var snapshots: [Timeframe: PeriodSnapshot] = [:]
            snapshots[.day] = PeriodSnapshot(history: try await history, fetchedAt: .now)
            for item in try await stats {
                switch item.periodType {
                case .sevenDays: snapshots[.week] = PeriodSnapshot(stats: item)
                case .ninetyDays: snapshots[.quarter] = PeriodSnapshot(stats: item)
                case nil: break
                }
            }
            if let status = await statuses?.first(where: { $0.monitorId == monitorID }) {
                self.status = status
            }
            self.snapshots = snapshots
            hasLoaded = true
            errorMessage = nil
        } catch is CancellationError {
        } catch let error as URLError where error.code == .cancelled {
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
