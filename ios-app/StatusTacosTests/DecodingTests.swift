import Foundation
import Testing
@testable import StatusTacos

private func utc(_ year: Int, _ month: Int, _ day: Int, _ hour: Int = 0, _ minute: Int = 0, _ second: Int = 0) -> Date {
    var calendar = Calendar(identifier: .gregorian)
    calendar.timeZone = TimeZone(identifier: "UTC")!
    return calendar.date(from: DateComponents(year: year, month: month, day: day, hour: hour, minute: minute, second: second))!
}

private func decode<T: Decodable>(_ type: T.Type, _ json: String) throws -> T {
    try JSONDecoder.backend().decode(type, from: Data(json.utf8))
}

struct BackendDateTests {
    @Test func parsesTimestampsAsUTC() {
        #expect(BackendDate.parse("2026-10-07T12:00:00") == utc(2026, 10, 7, 12))
        #expect(BackendDate.parse("2026-10-07T12:00:00Z") == utc(2026, 10, 7, 12))
    }

    @Test func parsesFractionsOfASecond() throws {
        let date = try #require(BackendDate.parse("2026-10-07T12:00:01.25"))
        #expect(abs(date.timeIntervalSince(utc(2026, 10, 7, 12, 0, 1)) - 0.25) < 0.000_001)
        // Java sends up to 9 digits.
        let nanos = try #require(BackendDate.parse("2026-10-07T12:00:01.123456789"))
        #expect(abs(nanos.timeIntervalSince(utc(2026, 10, 7, 12, 0, 1)) - 0.123456789) < 0.000_001)
    }

    @Test func parsesTimestampsWithoutSeconds() {
        #expect(BackendDate.parse("2026-10-07T12:30") == utc(2026, 10, 7, 12, 30))
    }

    @Test(arguments: [
        "", "2026-10-07", "2026-10-07T", "2026-10-07 12:00:00", "2026-13-01T00:00:00",
        "2026-02-30T00:00:00", "2026-10-07T12:00:00.", "2026-10-07T12:0x:00", "2026-10-07T12:00:00+02:00",
    ])
    func rejectsInvalidTimestamps(_ value: String) {
        #expect(BackendDate.parse(value) == nil)
    }
}

struct DecodingTests {
    @Test func decodesMonitors() throws {
        let monitors = try decode([MonitorDTO].self, """
        [{"id":7,"name":"API","url":"https://api.example.com/health","tenantId":1,
          "tenant":{"id":1,"name":"Default","code":"DEF","isActive":true,"createdAt":"2025-01-01T00:00:00"},
          "state":"SILENT","httpHeaders":{"X-Key":"1"},"alertingThreshold":3,
          "createdAt":"2025-06-26T10:15:30.123","updatedAt":"2025-06-26T10:15:30"},
         {"id":8,"name":"Old","url":"https://old.example.com","state":"PAUSED_FOREVER"}]
        """)
        #expect(monitors.count == 2)
        #expect(monitors[0].id == 7)
        #expect(monitors[0].state == .silent)
        #expect(monitors[0].tenant == Tenant(id: 1, name: "Default", code: "DEF"))
        #expect(monitors[0].alertingThreshold == 3)
        #expect(monitors[0].createdAt == BackendDate.parse("2025-06-26T10:15:30.123"))
        // Unknown states and left-out fields do not break the list.
        #expect(monitors[1].state == .unknown)
        #expect(monitors[1].tenant == nil)
        #expect(monitors[1].createdAt == nil)
    }

    @Test func decodesMonitorStatuses() throws {
        let statuses = try decode([MonitorStatusDTO].self, """
        [{"monitorId":7,"monitorName":"API","currentStatus":"down","lastCheckedAt":"2026-10-07T12:00:00",
          "lastUpAt":"2026-10-07T11:00:00","consecutiveFailures":4,"lastStatusCode":503},
         {"monitorId":8,"currentStatus":"up","lastResponseTimeMs":120}]
        """)
        #expect(statuses[0].currentStatus == .down)
        #expect(statuses[0].lastUpAt == utc(2026, 10, 7, 11))
        #expect(statuses[0].lastResponseTimeMs == nil)
        #expect(statuses[0].lastStatusCode == 503)
        #expect(statuses[1].currentStatus == .up)
        #expect(statuses[1].lastResponseTimeMs == 120)
    }

    @Test func decodesUptimeStats() throws {
        let stats = try decode(UptimeStatsDTO.self, """
        {"monitorId":7,"monitorName":"API","periodType":"SEVEN_DAYS",
         "periodStart":"2026-09-30T14:00:00","periodEnd":"2026-10-07T14:37:12",
         "intervalMinutes":60,"totalChecks":40000,"successfulChecks":39990,"uptimePercentage":99.97,
         "minResponseTimeMs":80,"maxResponseTimeMs":2400,"avgResponseTimeMs":130,"p99ResponseTimeMs":900,
         "responseTimeDataPoints":[{"timestamp":"2026-09-30T14:00:00","maxResponseTimeMs":300},
                                   {"timestamp":"2026-09-30T15:00:00"}],
         "statusDownPeriods":[{"start":"2026-10-01T10:00:00","end":"2026-10-01T10:15:00"}]}
        """)
        #expect(stats.periodType == .sevenDays)
        #expect(stats.periodStart == utc(2026, 9, 30, 14))
        #expect(stats.uptimePercentage == 99.97)
        #expect(stats.totalChecks == 40000)
        #expect(stats.p99ResponseTimeMs == 900)
        #expect(stats.responseTimeDataPoints?.map(\.maxResponseTimeMs) == [300, nil])
        #expect(stats.statusDownPeriods?.first?.duration == 900.0)
    }

    @Test func decodesStatsWithoutChecks() throws {
        // No checks: uptime and response times are left out.
        let stats = try decode(UptimeStatsDTO.self, """
        {"monitorId":9,"periodType":"NINETY_DAYS","periodStart":"2026-07-09T00:00:00",
         "periodEnd":"2026-10-07T14:37:12","totalChecks":0,"successfulChecks":0,
         "responseTimeDataPoints":[],"statusDownPeriods":[]}
        """)
        #expect(stats.periodType == .ninetyDays)
        #expect(stats.uptimePercentage == nil)
        #expect(stats.avgResponseTimeMs == nil)
        #expect(stats.firstCheckAt == nil)
    }

    @Test func decodesResponseTimeHistory() throws {
        let history = try decode(ResponseTimeHistoryDTO.self, """
        {"monitorId":7,"monitorName":"API","intervalMinutes":3,"totalDataPoints":2,
         "uptimePercentage24h":100.00,"totalChecks24h":5760,"successfulChecks24h":5760,
         "dataPoints":[{"timestamp":"2026-10-07T12:00:00","maxResponseTimeMs":95}],
         "statusDownPeriods":[],"firstCheckAt":"2026-10-06T14:40:00"}
        """)
        #expect(history.uptimePercentage24h == 100)
        #expect(history.firstCheckAt == utc(2026, 10, 6, 14, 40))
        #expect(history.dataPoints?.first?.timestamp == utc(2026, 10, 7, 12))
        #expect(history.statusDownPeriods?.isEmpty == true)
        // An older server: no response time summary.
        #expect(history.avgResponseTimeMs == nil)
        #expect(PeriodSnapshot(history: history, fetchedAt: utc(2026, 10, 7, 12)).responseTimes == nil)
    }

    @Test func decodesMonitorEnvironments() throws {
        let monitors = try decode([MonitorDTO].self, """
        [{"id":1,"name":"A","url":"https://a.com","state":"ACTIVE","environment":"PROD"},
         {"id":2,"name":"B","url":"https://b.com","state":"ACTIVE","environment":"stage"},
         {"id":3,"name":"C","url":"https://c.com","state":"ACTIVE","environment":"Dev"},
         {"id":4,"name":"D","url":"https://d.com","state":"ACTIVE","environment":"QA"},
         {"id":5,"name":"E","url":"https://e.com","state":"ACTIVE","environment":null},
         {"id":6,"name":"F","url":"https://f.com","state":"ACTIVE","environment":42},
         {"id":7,"name":"G","url":"https://g.com","state":"ACTIVE"}]
        """)
        // Unknown values, wrong types and older servers give no environment and do not break the list.
        #expect(monitors.map(\.environment) == [.prod, .stage, .dev, nil, nil, nil, nil])
    }

    @Test func environmentNamesAndOrder() {
        #expect(MonitorEnvironment.allCases.map(\.badgeText) == ["PROD", "STAGE", "DEV"])
        #expect(MonitorEnvironment.allCases.map(\.accessibilityName) == ["Production", "Stage", "Development"])
        #expect(MonitorEnvironment.prod < .stage && MonitorEnvironment.stage < .dev)
        #expect(MonitorEnvironment(wireValue: " prod ") == .prod)
        #expect(MonitorEnvironment(wireValue: "") == nil)
    }

    @Test func decodesNewStatusFields() throws {
        let statuses = try decode([MonitorStatusDTO].self, """
        [{"monitorId":7,"currentStatus":"down","lastErrorMessage":"Connection refused",
          "outageStartedAt":"2026-10-07T11:48:00"},
         {"monitorId":8,"currentStatus":"up"}]
        """)
        #expect(statuses[0].lastErrorMessage == "Connection refused")
        #expect(statuses[0].outageStartedAt == utc(2026, 10, 7, 11, 48))
        #expect(statuses[1].lastErrorMessage == nil)
        #expect(statuses[1].outageStartedAt == nil)
    }

    @Test func decodesResponseTimeSummaryOf24Hours() throws {
        let history = try decode(ResponseTimeHistoryDTO.self, """
        {"monitorId":7,"totalChecks24h":5760,"minResponseTimeMs":61,"maxResponseTimeMs":298,
         "avgResponseTimeMs":82,"p99ResponseTimeMs":140}
        """)
        #expect(history.minResponseTimeMs == 61)
        #expect(history.maxResponseTimeMs == 298)
        #expect(history.avgResponseTimeMs == 82)
        #expect(history.p99ResponseTimeMs == 140)
        let snapshot = PeriodSnapshot(history: history, fetchedAt: utc(2026, 10, 7, 12))
        #expect(snapshot.responseTimes == .init(average: 82, p99: 140, minimum: 61, maximum: 298))
    }

    @Test func periodPathValues() {
        #expect(StatsPeriod.sevenDays.pathValue == "seven_days")
        #expect(StatsPeriod.ninetyDays.pathValue == "ninety_days")
    }
}
