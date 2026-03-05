import SwiftUI

// MARK: - Notification Filter

private enum NotificationFilter: String, CaseIterable, Identifiable {
    case all
    case unread
    case high
    case medium
    case low

    var id: String { rawValue }

    var label: String {
        switch self {
        case .all: return "All"
        case .unread: return "Unread"
        case .high: return "High"
        case .medium: return "Medium"
        case .low: return "Low"
        }
    }

    var icon: String? {
        switch self {
        case .all: return nil
        case .unread: return "circle.fill"
        case .high: return "exclamationmark.triangle.fill"
        case .medium: return "arrow.up.right"
        case .low: return "info.circle"
        }
    }

    var color: Color {
        switch self {
        case .all: return Theme.primaryBlue
        case .unread: return Theme.accentOrange
        case .high: return Theme.accentRed
        case .medium: return Theme.accentOrange
        case .low: return Theme.accentCyan
        }
    }
}

// MARK: - Notifications View

struct AlertHistoryView: View {
    @ObservedObject var alertManager = AlertManager.shared
    @State private var selectedFilter: NotificationFilter = .all
    @State private var expandedAlertID: UUID?
    @State private var showClearConfirmation = false

    private var filteredAlerts: [SmartAlert] {
        switch selectedFilter {
        case .all: return alertManager.activeAlerts
        case .unread: return alertManager.activeAlerts.filter { !$0.isRead }
        case .high: return alertManager.activeAlerts.filter { $0.severity == .high }
        case .medium: return alertManager.activeAlerts.filter { $0.severity == .medium }
        case .low: return alertManager.activeAlerts.filter { $0.severity == .low }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // Sticky header with stats + filters
            headerSection

            // Content
            if alertManager.activeAlerts.isEmpty {
                emptyState
            } else if filteredAlerts.isEmpty {
                filteredEmptyState
            } else {
                notificationList
            }
        }
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle("Notifications")
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(Theme.surface, for: .navigationBar)
        .toolbarBackground(.visible, for: .navigationBar)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                            alertManager.markAllAsRead()
                        }
                    } label: {
                        Label("Mark All as Read", systemImage: "envelope.open")
                    }
                    .disabled(alertManager.unreadCount == 0)

                    Button(role: .destructive) {
                        showClearConfirmation = true
                    } label: {
                        Label("Clear All", systemImage: "trash")
                    }
                    .disabled(alertManager.activeAlerts.isEmpty)
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .font(.system(size: 16, weight: .medium))
                        .foregroundColor(Theme.textPrimary)
                }
            }
        }
        .confirmationDialog(
            "Clear all notifications?",
            isPresented: $showClearConfirmation,
            titleVisibility: .visible
        ) {
            Button("Clear All", role: .destructive) {
                withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                    alertManager.clearAllAlerts()
                }
            }
        } message: {
            Text("This will remove all \(alertManager.activeAlerts.count) notifications. This action cannot be undone.")
        }
    }

    // MARK: - Header Section

    private var headerSection: some View {
        VStack(spacing: 12) {
            // Stats bar
            HStack(spacing: 16) {
                StatPill(
                    icon: "bell.fill",
                    value: "\(alertManager.activeAlerts.count)",
                    label: "Total",
                    color: Theme.primaryBlue
                )

                StatPill(
                    icon: "circle.fill",
                    value: "\(alertManager.unreadCount)",
                    label: "Unread",
                    color: Theme.accentOrange
                )

                StatPill(
                    icon: "exclamationmark.triangle.fill",
                    value: "\(alertManager.activeAlerts.filter { $0.severity == .high }.count)",
                    label: "High",
                    color: Theme.accentRed
                )

                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)

            // Filter chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(NotificationFilter.allCases) { filter in
                        NotificationFilterChip(
                            filter: filter,
                            isSelected: selectedFilter == filter,
                            count: countFor(filter)
                        ) {
                            withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                                selectedFilter = filter
                            }
                        }
                    }
                }
                .padding(.horizontal, 20)
            }
            .padding(.bottom, 8)

            Divider().background(Color.white.opacity(0.06))
        }
        .background(Theme.surface.opacity(0.5))
    }

    // MARK: - Notification List

    private var notificationList: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(Array(groupedAlerts.enumerated()), id: \.element.key) { groupIndex, group in
                    // Group header
                    HStack {
                        Text(group.key.uppercased())
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(Theme.textMuted)
                        Spacer()
                        Text("\(group.value.count)")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(Theme.textMuted)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, groupIndex == 0 ? 16 : 20)
                    .padding(.bottom, 8)

                    // Alert cards in this group
                    VStack(spacing: 0) {
                        ForEach(Array(group.value.enumerated()), id: \.element.id) { index, alert in
                            if index > 0 {
                                Divider()
                                    .background(Color.white.opacity(0.05))
                                    .padding(.leading, 62)
                            }

                            NotificationCard(
                                alert: alert,
                                isExpanded: expandedAlertID == alert.id,
                                onTap: {
                                    withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                        if expandedAlertID == alert.id {
                                            expandedAlertID = nil
                                        } else {
                                            expandedAlertID = alert.id
                                            if !alert.isRead {
                                                alertManager.markAsRead(alert)
                                            }
                                        }
                                    }
                                },
                                onDelete: {
                                    withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                        alertManager.removeAlert(alert)
                                    }
                                },
                                onMarkRead: {
                                    withAnimation(.spring(response: 0.25, dampingFraction: 0.8)) {
                                        alertManager.markAsRead(alert)
                                    }
                                }
                            )
                        }
                    }
                    .background {
                        RoundedRectangle(cornerRadius: 14)
                            .fill(Theme.surface)
                            .overlay {
                                RoundedRectangle(cornerRadius: 14)
                                    .stroke(Color.white.opacity(0.05), lineWidth: 1)
                            }
                    }
                    .padding(.horizontal, 16)
                }
            }
            .padding(.bottom, 40)
        }
    }

    // MARK: - Empty States

    private var emptyState: some View {
        VStack(spacing: 20) {
            Spacer()

            ZStack {
                Circle()
                    .fill(Theme.surface)
                    .frame(width: 100, height: 100)

                Image(systemName: "bell.slash")
                    .font(.system(size: 40, weight: .light))
                    .foregroundColor(Theme.textMuted.opacity(0.5))
            }

            VStack(spacing: 8) {
                Text("No Notifications")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Text("Smart alerts will appear here when\nunusual market activity is detected")
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textSecondary)
                    .multilineTextAlignment(.center)
                    .lineSpacing(2)
            }

            HStack(spacing: 6) {
                Image(systemName: "bell.badge")
                    .font(.system(size: 12))
                Text("Make sure alerts are enabled in Settings")
                    .font(.system(size: 12))
            }
            .foregroundColor(Theme.textMuted)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background {
                Capsule()
                    .fill(Theme.surface)
            }

            Spacer()
            Spacer()
        }
    }

    private var filteredEmptyState: some View {
        VStack(spacing: 16) {
            Spacer()

            Image(systemName: "line.3.horizontal.decrease.circle")
                .font(.system(size: 36, weight: .light))
                .foregroundColor(Theme.textMuted.opacity(0.5))

            VStack(spacing: 6) {
                Text("No \(selectedFilter.label) Notifications")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)

                Text("Try a different filter to see more")
                    .font(.system(size: 13))
                    .foregroundColor(Theme.textSecondary)
            }

            Button {
                withAnimation { selectedFilter = .all }
            } label: {
                Text("Show All")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(Theme.primaryBlue)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .background {
                        Capsule()
                            .fill(Theme.primaryBlue.opacity(0.12))
                    }
            }

            Spacer()
            Spacer()
        }
    }

    // MARK: - Helpers

    private func countFor(_ filter: NotificationFilter) -> Int {
        switch filter {
        case .all: return alertManager.activeAlerts.count
        case .unread: return alertManager.unreadCount
        case .high: return alertManager.activeAlerts.filter { $0.severity == .high }.count
        case .medium: return alertManager.activeAlerts.filter { $0.severity == .medium }.count
        case .low: return alertManager.activeAlerts.filter { $0.severity == .low }.count
        }
    }

    private var groupedAlerts: [(key: String, value: [SmartAlert])] {
        let calendar = Calendar.current
        let now = Date()

        let grouped = Dictionary(grouping: filteredAlerts) { alert -> String in
            if calendar.isDateInToday(alert.timestamp) {
                let minutes = Int(now.timeIntervalSince(alert.timestamp) / 60)
                if minutes < 1 { return "Just Now" }
                if minutes < 5 { return "Last 5 Minutes" }
                if minutes < 15 { return "Last 15 Minutes" }
                if minutes < 60 { return "Last Hour" }
                return "Earlier Today"
            } else if calendar.isDateInYesterday(alert.timestamp) {
                return "Yesterday"
            } else {
                let formatter = DateFormatter()
                formatter.dateFormat = "dd MMM yyyy"
                return formatter.string(from: alert.timestamp)
            }
        }

        let order = ["Just Now", "Last 5 Minutes", "Last 15 Minutes", "Last Hour", "Earlier Today", "Yesterday"]
        return grouped.sorted { a, b in
            let aIndex = order.firstIndex(of: a.key) ?? Int.max
            let bIndex = order.firstIndex(of: b.key) ?? Int.max
            if aIndex != Int.max || bIndex != Int.max {
                return aIndex < bIndex
            }
            return (a.value.first?.timestamp ?? .distantPast) > (b.value.first?.timestamp ?? .distantPast)
        }
    }
}

// MARK: - Stat Pill

private struct StatPill: View {
    let icon: String
    let value: String
    let label: String
    let color: Color

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(color)

            Text(value)
                .font(.system(size: 14, weight: .bold, design: .rounded))
                .foregroundColor(Theme.textPrimary)

            Text(label)
                .font(.system(size: 10))
                .foregroundColor(Theme.textMuted)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background {
            Capsule()
                .fill(color.opacity(0.08))
                .overlay {
                    Capsule()
                        .stroke(color.opacity(0.15), lineWidth: 1)
                }
        }
    }
}

// MARK: - Filter Chip

private struct NotificationFilterChip: View {
    let filter: NotificationFilter
    let isSelected: Bool
    let count: Int
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 5) {
                if let icon = filter.icon {
                    Image(systemName: icon)
                        .font(.system(size: 8, weight: .bold))
                        .foregroundColor(isSelected ? .white : filter.color)
                }

                Text(filter.label)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(isSelected ? .white : Theme.textSecondary)

                if count > 0 {
                    Text("\(count)")
                        .font(.system(size: 10, weight: .bold, design: .rounded))
                        .foregroundColor(isSelected ? .white.opacity(0.8) : Theme.textMuted)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background {
                Capsule()
                    .fill(isSelected ? filter.color : Theme.surface)
                    .overlay {
                        Capsule()
                            .stroke(isSelected ? Color.clear : Color.white.opacity(0.08), lineWidth: 1)
                    }
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Notification Card

private struct NotificationCard: View {
    let alert: SmartAlert
    let isExpanded: Bool
    let onTap: () -> Void
    let onDelete: () -> Void
    let onMarkRead: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            // Main row
            HStack(alignment: .top, spacing: 12) {
                // Unread indicator + Icon
                ZStack(alignment: .topLeading) {
                    ZStack {
                        Circle()
                            .fill(alert.type.color.opacity(0.12))
                            .frame(width: 40, height: 40)

                        Image(systemName: alert.type.icon)
                            .font(.system(size: 17, weight: .medium))
                            .foregroundColor(alert.type.color)
                    }

                    // Unread dot
                    if !alert.isRead {
                        Circle()
                            .fill(Theme.accentOrange)
                            .frame(width: 10, height: 10)
                            .overlay {
                                Circle()
                                    .stroke(Theme.surface, lineWidth: 2)
                            }
                            .offset(x: -2, y: -2)
                    }
                }

                // Content
                VStack(alignment: .leading, spacing: 4) {
                    // Top row: badges + time
                    HStack(spacing: 6) {
                        Text(alert.type.displayName)
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(alert.type.color)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background {
                                Capsule()
                                    .fill(alert.type.color.opacity(0.1))
                            }

                        SeverityBadge(severity: alert.severity)

                        Spacer()

                        Text(timeAgo(alert.timestamp))
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(Theme.textMuted)
                    }

                    // Title
                    Text(alert.title)
                        .font(.system(size: 14, weight: alert.isRead ? .medium : .bold))
                        .foregroundColor(alert.isRead ? Theme.textSecondary : Theme.textPrimary)
                        .lineLimit(isExpanded ? nil : 1)

                    // Message
                    Text(alert.message)
                        .font(.system(size: 12))
                        .foregroundColor(Theme.textSecondary)
                        .lineLimit(isExpanded ? nil : 2)
                        .lineSpacing(1)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .contentShape(Rectangle())
            .onTapGesture(perform: onTap)

            // Expanded detail section
            if isExpanded {
                expandedContent
            }
        }
        .background(alert.isRead ? Color.clear : alert.type.color.opacity(0.02))
    }

    @ViewBuilder
    private var expandedContent: some View {
        VStack(spacing: 0) {
            Divider().background(Color.white.opacity(0.05)).padding(.leading, 62)

            VStack(alignment: .leading, spacing: 10) {
                // Strike & Option Type info
                if let strike = alert.strike, let optionType = alert.optionType {
                    HStack(spacing: 12) {
                        DetailTag(icon: "target", label: "Strike", value: String(format: "%.0f", strike))
                        DetailTag(icon: "arrow.up.arrow.down", label: "Type", value: optionType)
                    }
                }

                // Action hint
                if let hint = alert.actionHint {
                    HStack(spacing: 8) {
                        Image(systemName: "lightbulb.fill")
                            .font(.system(size: 12))
                            .foregroundColor(Theme.accentYellow)

                        Text(hint)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(Theme.accentYellow)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(Theme.accentYellow.opacity(0.06))
                    }
                }

                // Timestamp detail
                HStack(spacing: 6) {
                    Image(systemName: "clock")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)

                    Text(fullTimestamp(alert.timestamp))
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)
                }

                // Action buttons
                HStack(spacing: 10) {
                    if !alert.isRead {
                        Button(action: onMarkRead) {
                            HStack(spacing: 5) {
                                Image(systemName: "envelope.open")
                                    .font(.system(size: 11))
                                Text("Mark Read")
                                    .font(.system(size: 11, weight: .semibold))
                            }
                            .foregroundColor(Theme.primaryBlue)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background {
                                Capsule()
                                    .fill(Theme.primaryBlue.opacity(0.1))
                            }
                        }
                        .buttonStyle(.plain)
                    }

                    Button(action: onDelete) {
                        HStack(spacing: 5) {
                            Image(systemName: "trash")
                                .font(.system(size: 11))
                            Text("Delete")
                                .font(.system(size: 11, weight: .semibold))
                        }
                        .foregroundColor(Theme.accentRed)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background {
                            Capsule()
                                .fill(Theme.accentRed.opacity(0.1))
                        }
                    }
                    .buttonStyle(.plain)

                    Spacer()
                }
            }
            .padding(.horizontal, 14)
            .padding(.leading, 52)
            .padding(.vertical, 10)
            .padding(.bottom, 4)
        }
        .transition(.opacity.combined(with: .move(edge: .top)))
    }

    private func timeAgo(_ date: Date) -> String {
        let seconds = Int(Date().timeIntervalSince(date))
        if seconds < 60 { return "just now" }
        if seconds < 3600 { return "\(seconds / 60)m ago" }
        if seconds < 86400 { return "\(seconds / 3600)h ago" }
        let formatter = DateFormatter()
        formatter.dateFormat = "dd MMM"
        return formatter.string(from: date)
    }

    private func fullTimestamp(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd MMM yyyy, h:mm:ss a"
        return formatter.string(from: date)
    }
}

// MARK: - Severity Badge

private struct SeverityBadge: View {
    let severity: AlertSeverity

    var body: some View {
        HStack(spacing: 3) {
            Circle()
                .fill(severity.color)
                .frame(width: 5, height: 5)

            Text(severity.label)
                .font(.system(size: 8, weight: .heavy))
                .foregroundColor(severity.color)
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 2)
        .background {
            Capsule()
                .fill(severity.color.opacity(0.1))
        }
    }
}

// MARK: - Detail Tag

private struct DetailTag: View {
    let icon: String
    let label: String
    let value: String

    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: icon)
                .font(.system(size: 10))
                .foregroundColor(Theme.textMuted)

            Text(label)
                .font(.system(size: 10))
                .foregroundColor(Theme.textMuted)

            Text(value)
                .font(.system(size: 11, weight: .bold, design: .monospaced))
                .foregroundColor(Theme.textPrimary)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 5)
        .background {
            RoundedRectangle(cornerRadius: 6)
                .fill(Theme.surface)
                .overlay {
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(Color.white.opacity(0.06), lineWidth: 1)
                }
        }
    }
}
