//
//  StatusTacosAlertWidget.swift
//  StatusTacosAlertWidget
//

import SwiftUI
import WidgetKit

struct MonitorEntry: TimelineEntry {
    let date: Date
    let snapshot: MonitorSnapshot?
    let configuration: MonitorWidgetIntent
}

struct MonitorProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> MonitorEntry {
        MonitorEntry(date: .now, snapshot: .preview, configuration: MonitorWidgetIntent())
    }

    func snapshot(for configuration: MonitorWidgetIntent, in context: Context) async -> MonitorEntry {
        let snapshot = MonitorSnapshot.load() ?? (context.isPreview ? .preview : nil)
        return MonitorEntry(date: .now, snapshot: snapshot, configuration: configuration)
    }

    func timeline(for configuration: MonitorWidgetIntent, in context: Context) async -> Timeline<MonitorEntry> {
        // The app reloads the widget when a status changes, so the widget never polls.
        Timeline(entries: [MonitorEntry(date: .now, snapshot: MonitorSnapshot.load(), configuration: configuration)],
                 policy: .never)
    }
}

struct MonitorWidgetEntryView: View {
    @Environment(\.widgetFamily) private var family
    @Environment(\.widgetRenderingMode) private var renderingMode
    let entry: MonitorEntry

    var body: some View {
        MonitorWidgetView(snapshot: entry.snapshot, configuration: entry.configuration,
                          family: family, isFullColor: renderingMode == .fullColor)
            .containerBackground(.background, for: .widget)
            .widgetURL(MonitorSnapshot.openMonitorsURL)
    }
}

@main
struct StatusTacosAlertWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: MonitorSnapshot.widgetKind, intent: MonitorWidgetIntent.self,
                               provider: MonitorProvider()) { entry in
            MonitorWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Monitors")
        .description("See which monitors are up or down. Edit the widget to filter by tenant and environment.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

extension MonitorSnapshot {
    static let preview = MonitorSnapshot(
        monitors: [
            Monitor(name: "Website", tenantName: "Demo", status: .up, downSince: nil, environment: "PROD"),
            Monitor(name: "Checkout API", tenantName: "Demo", status: .down,
                    downSince: .now.addingTimeInterval(-1_260), environment: "PROD"),
            Monitor(name: "Database", tenantName: "Demo", status: .up, downSince: nil, environment: "STAGE"),
            Monitor(name: "Status page", tenantName: "Demo", status: .up, downSince: nil)
        ],
        notice: nil
    )
}

#Preview(as: .systemMedium) {
    StatusTacosAlertWidget()
} timeline: {
    MonitorEntry(date: .now, snapshot: .preview, configuration: MonitorWidgetIntent())
}
