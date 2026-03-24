import SwiftUI

// MARK: - Signal Filter

private enum SignalFilter: String, CaseIterable, Identifiable {
    case all
    case unread
    case bullish
    case bearish
    case volatility

    var id: String { rawValue }

    var label: String {
        switch self {
        case .all: return "All"
        case .unread: return "Unread"
        case .bullish: return "Bullish"
        case .bearish: return "Bearish"
        case .volatility: return "VIX / OI"
        }
    }

    var color: Color {
        switch self {
        case .all: return Theme.primaryBlue
        case .unread: return Theme.accentOrange
        case .bullish: return Theme.accentGreen
        case .bearish: return Theme.accentRed
        case .volatility: return Theme.accentPurple
        }
    }
}

// MARK: - Notification Center View

struct NotificationCenterView: View {
    @ObservedObject var signalStore = MarketSignalStore.shared
    @ObservedObject var alertManager = AlertManager.shared
    @State private var selectedFilter: SignalFilter = .all
    @State private var selectedTab: Int = 0 // 0 = market signals, 1 = smart alerts
    @State private var expandedSignalID: String?
    @State private var showClearConfirmation = false

    private var filteredSignals: [MarketSignal] {
        let signals = signalStore.signals
        switch selectedFilter {
        case .all: return signals
        case .unread: return signals.filter { !$0.isRead }
        case .bullish: return signals.filter { ["long_buildup", "short_covering"].contains($0.signalType) }
        case .bearish: return signals.filter { ["short_buildup", "long_unwinding"].contains($0.signalType) }
        case .volatility: return signals.filter { ["vix_change", "oi_surge", "pcr_shift"].contains($0.signalType) }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // Tab selector: Market Signals vs Smart Alerts
            segmentedPicker

            if selectedTab == 0 {
                marketSignalsContent
            } else {
                AlertHistoryView()
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
                            if selectedTab == 0 {
                                signalStore.markAllAsRead()
                            } else {
                                alertManager.markAllAsRead()
                            }
                        }
                    } label: {
                        Label("Mark All as Read", systemImage: "envelope.open")
                    }

                    Button(role: .destructive) {
                        showClearConfirmation = true
                    } label: {
                        Label("Clear All", systemImage: "trash")
                    }
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
                    if selectedTab == 0 {
                        signalStore.clearAll()
                    } else {
                        alertManager.clearAllAlerts()
                    }
                }
            }
        }
    }

    // MARK: - Segmented Picker

    private var segmentedPicker: some View {
        HStack(spacing: 0) {
            tabButton(title: "Market Signals", count: signalStore.unreadCount, index: 0)
            tabButton(title: "Smart Alerts", count: alertManager.unreadCount, index: 1)
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 4)
        .background(Theme.surface.opacity(0.5))
    }

    private func tabButton(title: String, count: Int, index: Int) -> some View {
        Button {
            withAnimation(.spring(response: 0.25, dampingFraction: 0.85)) {
                selectedTab = index
            }
        } label: {
            VStack(spacing: 6) {
                HStack(spacing: 6) {
                    Text(title)
                        .font(.system(size: 13, weight: selectedTab == index ? .bold : .medium))
                        .foregroundColor(selectedTab == index ? Theme.textPrimary : Theme.textMuted)

                    if count > 0 {
                        Text("\(count)")
                            .font(.system(size: 10, weight: .bold, design: .rounded))
                            .foregroundColor(.white)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 1)
                            .background(Capsule().fill(Theme.accentOrange))
                    }
                }

                Rectangle()
                    .fill(selectedTab == index ? Theme.primaryBlue : Color.clear)
                    .frame(height: 2)
                    .cornerRadius(1)
            }
        }
        .buttonStyle(.plain)
        .frame(maxWidth: .infinity)
    }

    // MARK: - Market Signals Content

    private var marketSignalsContent: some View {
        VStack(spacing: 0) {
            // Stats + filter chips
            signalHeader

            if signalStore.signals.isEmpty {
                signalEmptyState
            } else if filteredSignals.isEmpty {
                filteredEmptyState
            } else {
                signalList
            }
        }
    }

    // MARK: - Signal Header

    private var signalHeader: some View {
        VStack(spacing: 10) {
            // Stats bar
            HStack(spacing: 12) {
                SignalStatPill(icon: "antenna.radiowaves.left.and.right", value: "\(signalStore.signals.count)", label: "Total", color: Theme.primaryBlue)
                SignalStatPill(icon: "circle.fill", value: "\(signalStore.unreadCount)", label: "New", color: Theme.accentOrange)
                SignalStatPill(
                    icon: "chart.line.uptrend.xyaxis",
                    value: "\(signalStore.signals.filter { ["long_buildup", "short_covering"].contains($0.signalType) }.count)",
                    label: "Bullish",
                    color: Theme.accentGreen
                )
                SignalStatPill(
                    icon: "chart.line.downtrend.xyaxis",
                    value: "\(signalStore.signals.filter { ["short_buildup", "long_unwinding"].contains($0.signalType) }.count)",
                    label: "Bearish",
                    color: Theme.accentRed
                )
                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.top, 10)

            // Filter chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(SignalFilter.allCases) { filter in
                        SignalFilterChip(
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
            .padding(.bottom, 6)

            Divider().background(Color.white.opacity(0.06))
        }
        .background(Theme.surface.opacity(0.3))
    }

    // MARK: - Signal List

    private var signalList: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(Array(groupedSignals.enumerated()), id: \.element.key) { groupIndex, group in
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
                    .padding(.top, groupIndex == 0 ? 14 : 18)
                    .padding(.bottom, 6)

                    // Signal cards
                    VStack(spacing: 0) {
                        ForEach(Array(group.value.enumerated()), id: \.element.id) { index, signal in
                            if index > 0 {
                                Divider()
                                    .background(Color.white.opacity(0.05))
                                    .padding(.leading, 62)
                            }
                            SignalCard(
                                signal: signal,
                                isExpanded: expandedSignalID == signal.id,
                                onTap: {
                                    withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                        if expandedSignalID == signal.id {
                                            expandedSignalID = nil
                                        } else {
                                            expandedSignalID = signal.id
                                            if !signal.isRead {
                                                signalStore.markAsRead(signal)
                                            }
                                        }
                                    }
                                },
                                onDelete: {
                                    withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                        signalStore.deleteSignal(signal)
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

    private var signalEmptyState: some View {
        VStack(spacing: 20) {
            Spacer()
            ZStack {
                Circle()
                    .fill(Theme.surface)
                    .frame(width: 100, height: 100)
                Image(systemName: "antenna.radiowaves.left.and.right.slash")
                    .font(.system(size: 40, weight: .light))
                    .foregroundColor(Theme.textMuted.opacity(0.5))
            }
            VStack(spacing: 8) {
                Text("No Market Signals")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(Theme.textPrimary)
                Text("Market signals like PCR shifts, OI buildups,\nVIX spikes will appear here during market hours")
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textSecondary)
                    .multilineTextAlignment(.center)
                    .lineSpacing(2)
            }
            HStack(spacing: 6) {
                Image(systemName: "clock").font(.system(size: 12))
                Text("Signals are checked every 60 seconds")
                    .font(.system(size: 12))
            }
            .foregroundColor(Theme.textMuted)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(Capsule().fill(Theme.surface))
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
                Text("No \(selectedFilter.label) Signals")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
                Text("Try a different filter")
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
                    .background(Capsule().fill(Theme.primaryBlue.opacity(0.12)))
            }
            Spacer()
            Spacer()
        }
    }

    // MARK: - Helpers

    private func countFor(_ filter: SignalFilter) -> Int {
        switch filter {
        case .all: return signalStore.signals.count
        case .unread: return signalStore.unreadCount
        case .bullish: return signalStore.signals.filter { ["long_buildup", "short_covering"].contains($0.signalType) }.count
        case .bearish: return signalStore.signals.filter { ["short_buildup", "long_unwinding"].contains($0.signalType) }.count
        case .volatility: return signalStore.signals.filter { ["vix_change", "oi_surge", "pcr_shift"].contains($0.signalType) }.count
        }
    }

    private var groupedSignals: [(key: String, value: [MarketSignal])] {
        let calendar = Calendar.current
        let now = Date()

        let grouped = Dictionary(grouping: filteredSignals) { signal -> String in
            if calendar.isDateInToday(signal.timestamp) {
                let minutes = Int(now.timeIntervalSince(signal.timestamp) / 60)
                if minutes < 1 { return "Just Now" }
                if minutes < 5 { return "Last 5 Minutes" }
                if minutes < 15 { return "Last 15 Minutes" }
                if minutes < 60 { return "Last Hour" }
                return "Earlier Today"
            } else if calendar.isDateInYesterday(signal.timestamp) {
                return "Yesterday"
            } else {
                let formatter = DateFormatter()
                formatter.dateFormat = "dd MMM yyyy"
                return formatter.string(from: signal.timestamp)
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

// MARK: - Signal Card

private struct SignalCard: View {
    let signal: MarketSignal
    let isExpanded: Bool
    let onTap: () -> Void
    let onDelete: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HStack(alignment: .top, spacing: 12) {
                // Icon with unread dot
                ZStack(alignment: .topLeading) {
                    ZStack {
                        Circle()
                            .fill(Color(hex: signal.color).opacity(0.12))
                            .frame(width: 40, height: 40)
                        Image(systemName: signal.icon)
                            .font(.system(size: 17, weight: .medium))
                            .foregroundColor(Color(hex: signal.color))
                    }
                    if !signal.isRead {
                        Circle()
                            .fill(Theme.accentOrange)
                            .frame(width: 10, height: 10)
                            .overlay(Circle().stroke(Theme.surface, lineWidth: 2))
                            .offset(x: -2, y: -2)
                    }
                }

                VStack(alignment: .leading, spacing: 4) {
                    // Top row: badges + time
                    HStack(spacing: 6) {
                        Text(signal.sentiment.uppercased())
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(Color(hex: signal.color))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Capsule().fill(Color(hex: signal.color).opacity(0.1)))

                        if !signal.index.isEmpty {
                            Text(shortIndex(signal.index))
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(Theme.primaryBlue)
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Capsule().fill(Theme.primaryBlue.opacity(0.1)))
                        }

                        Spacer()

                        Text(timeAgo(signal.timestamp))
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(Theme.textMuted)
                    }

                    Text(signal.title)
                        .font(.system(size: 14, weight: signal.isRead ? .medium : .bold))
                        .foregroundColor(signal.isRead ? Theme.textSecondary : Theme.textPrimary)
                        .lineLimit(isExpanded ? nil : 1)

                    Text(signal.body)
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

            if isExpanded {
                expandedContent
            }
        }
        .background(signal.isRead ? Color.clear : Color(hex: signal.color).opacity(0.02))
    }

    @ViewBuilder
    private var expandedContent: some View {
        VStack(spacing: 0) {
            Divider().background(Color.white.opacity(0.05)).padding(.leading, 62)

            VStack(alignment: .leading, spacing: 10) {
                // Timestamp
                HStack(spacing: 6) {
                    Image(systemName: "clock")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)
                    Text(fullTimestamp(signal.timestamp))
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)
                }

                // Signal type badge
                HStack(spacing: 8) {
                    Image(systemName: "tag.fill")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)
                    Text(signalTypeLabel(signal.signalType))
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Theme.textSecondary)
                }

                // Delete button
                HStack {
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
                        .background(Capsule().fill(Theme.accentRed.opacity(0.1)))
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

    private func shortIndex(_ name: String) -> String {
        switch name {
        case "NIFTY 50": return "NIFTY"
        case "NIFTY BANK": return "BANKNIFTY"
        default: return name
        }
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

    private func signalTypeLabel(_ type: String) -> String {
        switch type {
        case "long_buildup": return "Long Buildup — Price up + OI up"
        case "short_buildup": return "Short Buildup — Price down + OI up"
        case "long_unwinding": return "Long Unwinding — Price down + OI down"
        case "short_covering": return "Short Covering — Price up + OI down"
        case "pcr_shift": return "PCR Shift — Put/Call ratio changed"
        case "vix_change": return "VIX Change — Volatility spike/drop"
        case "oi_surge": return "OI Surge — Major position buildup"
        case "max_pain_shift": return "Max Pain Shift — Institutional view"
        default: return type
        }
    }
}

// MARK: - Signal Stat Pill

private struct SignalStatPill: View {
    let icon: String
    let value: String
    let label: String
    let color: Color

    var body: some View {
        HStack(spacing: 5) {
            Image(systemName: icon)
                .font(.system(size: 9, weight: .bold))
                .foregroundColor(color)
            Text(value)
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundColor(Theme.textPrimary)
            Text(label)
                .font(.system(size: 9))
                .foregroundColor(Theme.textMuted)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 5)
        .background {
            Capsule()
                .fill(color.opacity(0.08))
                .overlay(Capsule().stroke(color.opacity(0.15), lineWidth: 1))
        }
    }
}

// MARK: - Signal Filter Chip

private struct SignalFilterChip: View {
    let filter: SignalFilter
    let isSelected: Bool
    let count: Int
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 5) {
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
                    .overlay(Capsule().stroke(isSelected ? Color.clear : Color.white.opacity(0.08), lineWidth: 1))
            }
        }
        .buttonStyle(.plain)
    }
}
