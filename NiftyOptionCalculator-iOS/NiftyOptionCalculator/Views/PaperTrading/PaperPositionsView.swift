import SwiftUI

// MARK: - Paper Positions View

struct PaperPositionsView: View {
    @ObservedObject var viewModel: PaperTradingViewModel
    @State private var positionToSquareOff: PaperPosition?

    var body: some View {
        VStack(spacing: 0) {
            if viewModel.hasOpenPositions {
                // Summary Header
                PositionsSummaryHeader(viewModel: viewModel)

                // Positions List
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(viewModel.portfolio.openPositions) { position in
                            PositionCard(
                                position: position,
                                onSquareOff: {
                                    positionToSquareOff = position
                                }
                            )
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .padding(.bottom, 120)
                }
            } else {
                // Empty State
                EmptyPositionsView()
            }
        }
        .sheet(item: $positionToSquareOff) { position in
            SquareOffSheet(viewModel: viewModel, position: position)
        }
    }
}

// MARK: - Positions Summary Header

private struct PositionsSummaryHeader: View {
    @ObservedObject var viewModel: PaperTradingViewModel

    private var totalUnrealizedPnL: Double {
        viewModel.portfolio.totalUnrealizedPnL
    }

    var body: some View {
        HStack(spacing: 16) {
            // Unrealized P&L
            VStack(alignment: .leading, spacing: 2) {
                Text(L.paperTradeUnrealizedPnL)
                    .font(.system(size: 11))
                    .foregroundColor(Theme.textMuted)

                Text(viewModel.formatPnL(totalUnrealizedPnL))
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .foregroundColor(totalUnrealizedPnL >= 0 ? Theme.profit : Theme.loss)
            }

            Spacer()

            // Positions Count
            VStack(alignment: .trailing, spacing: 2) {
                Text("Open Positions")
                    .font(.system(size: 11))
                    .foregroundColor(Theme.textMuted)

                Text("\(viewModel.openPositionsCount)")
                    .font(.system(size: 18, weight: .bold, design: .rounded))
                    .foregroundColor(Theme.textPrimary)
            }
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .background(Theme.surface.opacity(0.5))
    }
}

// MARK: - Position Card

private struct PositionCard: View {
    let position: PaperPosition
    let onSquareOff: () -> Void

    @State private var showDetails = false

    var body: some View {
        VStack(spacing: 0) {
            // Main Content
            Button {
                withAnimation(.spring(response: 0.3)) {
                    showDetails.toggle()
                }
            } label: {
                VStack(spacing: 12) {
                    // Top Row: Index & Direction
                    HStack {
                        // Index Badge
                        HStack(spacing: 6) {
                            if let index = TradingIndex(rawValue: position.index) {
                                Image(systemName: index.icon)
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(Color(hex: index.themeColor))
                            }

                            Text(position.index)
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(Theme.textPrimary)
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background {
                            Capsule()
                                .fill(Theme.card)
                        }

                        Spacer()

                        // Direction Badge
                        Text(position.direction.displayName)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(position.direction.color)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background {
                                Capsule()
                                    .fill(position.direction.color.opacity(0.15))
                            }

                        // Quantity Badge
                        Text("\(position.quantity)L")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(Theme.textSecondary)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 5)
                            .background {
                                Capsule()
                                    .fill(Theme.card)
                            }
                    }

                    // Middle Row: Strike & LTP
                    HStack(alignment: .bottom) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(position.optionSymbol)
                                .font(.system(size: 22, weight: .bold, design: .rounded))
                                .foregroundColor(Theme.textPrimary)

                            Text("Entry: \(position.displayEntryPrice)")
                                .font(.system(size: 12))
                                .foregroundColor(Theme.textMuted)
                        }

                        Spacer()

                        VStack(alignment: .trailing, spacing: 2) {
                            Text(position.displayCurrentLTP)
                                .font(.system(size: 22, weight: .bold, design: .monospaced))
                                .foregroundColor(Theme.accentGreen)

                            Text("LTP")
                                .font(.system(size: 11))
                                .foregroundColor(Theme.textMuted)
                        }
                    }

                    // Bottom Row: P&L
                    HStack {
                        // Unrealized P&L
                        HStack(spacing: 6) {
                            Image(systemName: position.unrealizedPnL >= 0 ? "arrow.up.right" : "arrow.down.right")
                                .font(.system(size: 12, weight: .bold))

                            Text(formatPnL(position.unrealizedPnL))
                                .font(.system(size: 15, weight: .bold, design: .rounded))

                            Text(formatPercent(position.unrealizedPnLPercent))
                                .font(.system(size: 12, weight: .medium))
                        }
                        .foregroundColor(position.unrealizedPnL >= 0 ? Theme.profit : Theme.loss)

                        Spacer()

                        // Days to Expiry
                        HStack(spacing: 4) {
                            Image(systemName: "clock")
                                .font(.system(size: 11))

                            Text("\(position.daysToExpiry)D")
                                .font(.system(size: 12, weight: .semibold))
                        }
                        .foregroundColor(position.daysToExpiry <= 2 ? Theme.loss : Theme.textMuted)
                    }
                }
                .padding(16)
                .background {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Theme.surface)
                        .overlay {
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(position.unrealizedPnL >= 0 ? Theme.profit.opacity(0.2) : Theme.loss.opacity(0.2), lineWidth: 1)
                        }
                }
            }
            .buttonStyle(.plain)

            // Expanded Details
            if showDetails {
                VStack(spacing: 12) {
                    Divider()
                        .background(Theme.card)

                    // Details Grid
                    HStack(spacing: 16) {
                        DetailItem(label: "Total Qty", value: "\(position.totalQuantity)")
                        DetailItem(label: "Investment", value: formatValue(position.investmentValue))
                        DetailItem(label: "Current", value: formatValue(position.currentValue))
                    }

                    // Square Off Button
                    Button {
                        let impact = UIImpactFeedbackGenerator(style: .medium)
                        impact.impactOccurred()
                        onSquareOff()
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 16, weight: .bold))

                            Text(L.paperTradeSquareOff)
                                .font(.system(size: 14, weight: .bold))
                        }
                        .foregroundColor(Theme.textPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background {
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Theme.accentOrange)
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 16)
                .background {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Theme.surface)
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
    }

    private func formatPnL(_ value: Double) -> String {
        let prefix = value >= 0 ? "+" : ""
        if abs(value) >= 1_00_000 {
            return String(format: "%@₹%.1fL", prefix, value / 1_00_000)
        }
        return String(format: "%@₹%.0f", prefix, value)
    }

    private func formatPercent(_ value: Double) -> String {
        let prefix = value >= 0 ? "+" : ""
        return String(format: "(%@%.1f%%)", prefix, value)
    }

    private func formatValue(_ value: Double) -> String {
        if value >= 1_00_000 {
            return String(format: "₹%.1fL", value / 1_00_000)
        }
        return String(format: "₹%.0f", value)
    }
}

private struct DetailItem: View {
    let label: String
    let value: String

    var body: some View {
        VStack(spacing: 4) {
            Text(label)
                .font(.system(size: 10))
                .foregroundColor(Theme.textMuted)

            Text(value)
                .font(.system(size: 13, weight: .semibold, design: .monospaced))
                .foregroundColor(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Empty Positions View

private struct EmptyPositionsView: View {
    var body: some View {
        VStack(spacing: 20) {
            Spacer()

            // Icon
            ZStack {
                Circle()
                    .fill(Theme.accentOrange.opacity(0.1))
                    .frame(width: 100, height: 100)

                Image(systemName: "list.bullet.rectangle")
                    .font(.system(size: 40))
                    .foregroundColor(Theme.accentOrange)
            }

            VStack(spacing: 8) {
                Text(L.paperTradeNoPositions)
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Text(L.paperTradeStartTrading)
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textSecondary)
                    .multilineTextAlignment(.center)
            }

            // Instruction Card
            VStack(alignment: .leading, spacing: 12) {
                InstructionRow(number: 1, text: "Go to Option Chain tab")
                InstructionRow(number: 2, text: "Long press on any option")
                InstructionRow(number: 3, text: "Select \"Paper Buy\" or \"Paper Sell\"")
            }
            .padding(20)
            .background {
                RoundedRectangle(cornerRadius: 16)
                    .fill(Theme.surface)
            }
            .padding(.horizontal, 40)

            Spacer()
            Spacer()
        }
    }
}

private struct InstructionRow: View {
    let number: Int
    let text: String

    var body: some View {
        HStack(spacing: 12) {
            Text("\(number)")
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Theme.textPrimary)
                .frame(width: 24, height: 24)
                .background {
                    Circle()
                        .fill(Theme.accentOrange)
                }

            Text(text)
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)
        }
    }
}

// MARK: - Preview

#Preview {
    ZStack {
        Theme.backgroundGradient.ignoresSafeArea()
        PaperPositionsView(viewModel: PaperTradingViewModel())
    }
}
