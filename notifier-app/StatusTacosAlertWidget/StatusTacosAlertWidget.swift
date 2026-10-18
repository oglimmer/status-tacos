//
//  StatusTacosAlertWidget.swift
//  StatusTacosAlertWidget
//

import SwiftUI
import WidgetKit

struct MonitorEntry: TimelineEntry {
    let date: Date
    let snapshot: MonitorSnapshot?
}

struct MonitorProvider: TimelineProvider {
    func placeholder(in context: Context) -> MonitorEntry {
        MonitorEntry(date: .now, snapshot: .preview)
    }

    func getSnapshot(in context: Context, completion: @escaping (MonitorEntry) -> Void) {
        let snapshot = MonitorSnapshot.load() ?? (context.isPreview ? .preview : nil)
        completion(MonitorEntry(date: .now, snapshot: snapshot))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MonitorEntry>) -> Void) {
        // The app reloads the widget when a status changes, so the widget never polls.
        completion(Timeline(entries: [MonitorEntry(date: .now, snapshot: MonitorSnapshot.load())], policy: .never))
    }
}

struct MonitorWidgetEntryView: View {
    @Environment(\.widgetFamily) private var family
    @Environment(\.widgetRenderingMode) private var renderingMode
    let entry: MonitorEntry

    var body: some View {
        MonitorWidgetView(snapshot: entry.snapshot, family: family, isFullColor: renderingMode == .fullColor)
            .containerBackground(Brand.paper, for: .widget)
            .widgetURL(MonitorSnapshot.openMonitorsURL)
    }
}

@main
struct StatusTacosAlertWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: MonitorSnapshot.widgetKind, provider: MonitorProvider()) { entry in
            MonitorWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Monitors")
        .description("See at a glance which monitors are up or down.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

extension MonitorSnapshot {
    static let preview = MonitorSnapshot(
        monitors: [
            Monitor(name: "Website", tenantName: "Demo", status: .up, downSince: nil),
            Monitor(name: "Checkout API", tenantName: "Demo", status: .down, downSince: .now.addingTimeInterval(-1_260)),
            Monitor(name: "Database", tenantName: "Demo", status: .up, downSince: nil),
            Monitor(name: "Status page", tenantName: "Demo", status: .up, downSince: nil)
        ],
        notice: nil
    )
}

#Preview(as: .systemSmall) {
    StatusTacosAlertWidget()
} timeline: {
    MonitorEntry(date: .now, snapshot: .preview)
}
