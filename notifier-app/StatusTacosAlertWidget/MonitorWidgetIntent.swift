//
//  MonitorWidgetIntent.swift
//  StatusTacosAlertWidget
//
//  The settings of one widget. The user sets them with "Edit Widget".
//

import AppIntents
import WidgetKit

struct MonitorWidgetIntent: WidgetConfigurationIntent {
    static var title: LocalizedStringResource = "Monitors"
    static var description = IntentDescription("Pick the monitors this widget shows.")

    @Parameter(title: "Tenants", description: "Empty shows all tenants.")
    var tenants: [TenantEntity]?

    @Parameter(title: "Environments", description: "Empty shows all environments.")
    var environments: [EnvironmentOption]?

    @Parameter(title: "Show", default: .all)
    var mode: ListMode

    @Parameter(title: "Show paused monitors", default: false)
    var showsPaused: Bool

    init() {}

    /// The monitors that pass the filters of this widget.
    func filter(_ monitors: [MonitorSnapshot.Monitor]) -> [MonitorSnapshot.Monitor] {
        let tenantNames = Set((tenants ?? []).map(\.id))
        let environments = Set(environments ?? [])
        return monitors.filter { monitor in
            (tenantNames.isEmpty || tenantNames.contains(monitor.tenantName))
                && (environments.isEmpty || environments.contains(EnvironmentOption(monitor)))
                && (showsPaused || monitor.isPaused != true)
                && (mode == .all || monitor.isDown)
        }
    }

    /// A short text of the active filters, for example "PROD · Acme". Nil when nothing is filtered.
    var filterSummary: String? {
        var parts: [String] = []
        if let environments, !environments.isEmpty {
            parts.append(EnvironmentOption.allCases.filter(environments.contains).map(\.shortTitle).joined(separator: ", "))
        }
        if let tenants, !tenants.isEmpty {
            parts.append(tenants.count == 1 ? tenants[0].id : "\(tenants.count) tenants")
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }
}

// MARK: - Tenant

/// The snapshot has only tenant names, so the name is the ID.
struct TenantEntity: AppEntity {
    let id: String

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Tenant"
    static var defaultQuery = TenantQuery()

    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(id)") }
}

struct TenantQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [TenantEntity] {
        identifiers.map(TenantEntity.init)
    }

    /// The tenants of the last data that the app wrote.
    func suggestedEntities() async throws -> [TenantEntity] {
        let names = Set((MonitorSnapshot.load()?.monitors ?? []).map(\.tenantName))
        return names.sorted { $0.localizedStandardCompare($1) == .orderedAscending }.map(TenantEntity.init)
    }
}

// MARK: - Environment

enum EnvironmentOption: String, AppEnum {
    case prod, stage, dev, none

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Environment"
    static var caseDisplayRepresentations: [Self: DisplayRepresentation] = [
        .prod: "Production",
        .stage: "Stage",
        .dev: "Development",
        .none: "No environment"
    ]

    init(_ monitor: MonitorSnapshot.Monitor) {
        switch monitor.environment?.uppercased() {
        case "PROD": self = .prod
        case "STAGE": self = .stage
        case "DEV": self = .dev
        default: self = .none
        }
    }

    var shortTitle: String {
        switch self {
        case .prod: return "PROD"
        case .stage: return "STAGE"
        case .dev: return "DEV"
        case .none: return "No env"
        }
    }
}

// MARK: - Mode

enum ListMode: String, AppEnum {
    case all, down

    static var typeDisplayRepresentation: TypeDisplayRepresentation = "Show"
    static var caseDisplayRepresentations: [Self: DisplayRepresentation] = [
        .all: "All monitors",
        .down: "Only down monitors"
    ]
}
