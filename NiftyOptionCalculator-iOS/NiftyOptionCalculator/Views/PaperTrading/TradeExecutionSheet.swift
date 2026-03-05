import SwiftUI

// MARK: - Trade Execution Sheet

struct TradeExecutionSheet: View {
    @ObservedObject var viewModel: PaperTradingViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ZStack {
                Theme.backgroundGradient.ignoresSafeArea()

                if let request = viewModel.tradeRequest {
                    ScrollView {
                        VStack(spacing: 20) {
                            // Option Info Card
                            OptionInfoCard(request: request)

                            // Quantity Selector
                            QuantitySelectorCard(
                                request: request,
                                onQuantityChange: { viewModel.updateTradeQuantity($0) }
                            )

                            // Stop Loss & Target
                            StopLossTargetCard(
                                request: request,
                                onStopLossChange: { viewModel.updateStopLoss($0) },
                                onTargetChange: { viewModel.updateTarget($0) }
                            )

                            // Order Summary
                            OrderSummaryCard(
                                request: request,
                                availableBalance: viewModel.portfolio.cashBalance,
                                availableMargin: viewModel.portfolio.availableMargin
                            )

                            // Execute Button
                            ExecuteButton(
                                request: request,
                                canExecute: viewModel.canExecuteTrade(request),
                                onExecute: { viewModel.executeTrade(request) }
                            )
                        }
                        .padding(20)
                    }
                } else {
                    Text("No trade request")
                        .foregroundColor(Theme.textMuted)
                }
            }
            .navigationTitle(viewModel.tradeRequest?.direction == .buy ? L.paperTradeBuy : L.paperTradeSell)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(Theme.textMuted)
                    }
                }
            }
        }

        .alert(L.commonError, isPresented: $viewModel.showError) {
            Button(L.commonOK, role: .cancel) {}
        } message: {
            Text(viewModel.errorMessage ?? "An error occurred")
        }
    }
}

// MARK: - Option Info Card

private struct OptionInfoCard: View {
    let request: TradeExecutionRequest

    var body: some View {
        VStack(spacing: 16) {
            // Index Badge
            HStack {
                Image(systemName: request.index.icon)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Color(hex: request.index.themeColor))

                Text(request.index.displayName)
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Spacer()

                // Direction Badge
                Text(request.direction.displayName)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(request.direction.color)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background {
                        Capsule()
                            .fill(request.direction.color.opacity(0.15))
                    }
            }

            // Strike & Type
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Strike Price")
                        .font(.system(size: 12))
                        .foregroundColor(Theme.textMuted)

                    Text(String(format: "%.0f %@", request.strikePrice, request.optionType.shortName))
                        .font(.system(size: 28, weight: .bold, design: .rounded))
                        .foregroundColor(Theme.textPrimary)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 4) {
                    Text("LTP")
                        .font(.system(size: 12))
                        .foregroundColor(Theme.textMuted)

                    Text(String(format: "₹%.2f", request.price))
                        .font(.system(size: 24, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.accentGreen)
                }
            }

            // Expiry
            HStack {
                Image(systemName: "calendar")
                    .font(.system(size: 12))
                    .foregroundColor(Theme.textMuted)

                Text("Expiry: ")
                    .font(.system(size: 12))
                    .foregroundColor(Theme.textMuted)

                Text(formatExpiry(request.expiryDate))
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(Theme.textSecondary)

                Spacer()

                Text("Lot Size: \(request.index.lotSize)")
                    .font(.system(size: 12))
                    .foregroundColor(Theme.textMuted)
            }
        }
        .padding(20)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
                .overlay {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.white.opacity(0.05), lineWidth: 1)
                }
        }
    }

    private func formatExpiry(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "dd MMM yyyy"
        return formatter.string(from: date)
    }
}

// MARK: - Quantity Selector Card

private struct QuantitySelectorCard: View {
    let request: TradeExecutionRequest
    let onQuantityChange: (Int) -> Void

    @State private var quantity: Int = 1

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Text(L.paperTradeQuantity)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Theme.textSecondary)

                Spacer()

                Text("\(quantity) lot\(quantity > 1 ? "s" : "") = \(quantity * request.index.lotSize) qty")
                    .font(.system(size: 12))
                    .foregroundColor(Theme.textMuted)
            }

            HStack(spacing: 20) {
                // Minus Button
                Button {
                    if quantity > 1 {
                        quantity -= 1
                        onQuantityChange(quantity)
                    }
                } label: {
                    Image(systemName: "minus.circle.fill")
                        .font(.system(size: 36))
                        .foregroundColor(quantity > 1 ? Theme.loss : Theme.textDisabled)
                }
                .disabled(quantity <= 1)

                // Quantity Display
                Text("\(quantity)")
                    .font(.system(size: 48, weight: .bold, design: .rounded))
                    .foregroundColor(Theme.textPrimary)
                    .frame(minWidth: 80)

                // Plus Button
                Button {
                    quantity += 1
                    onQuantityChange(quantity)
                } label: {
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 36))
                        .foregroundColor(Theme.profit)
                }
            }
            .padding(.vertical, 8)

            // Quick quantity buttons
            HStack(spacing: 12) {
                ForEach([1, 2, 5, 10], id: \.self) { qty in
                    Button {
                        quantity = qty
                        onQuantityChange(qty)
                    } label: {
                        Text("\(qty)")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(quantity == qty ? .white : Theme.textSecondary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background {
                                RoundedRectangle(cornerRadius: 10)
                                    .fill(quantity == qty ? Theme.accentBlue : Theme.card)
                            }
                    }
                }
            }
        }
        .padding(20)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
                .overlay {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.white.opacity(0.05), lineWidth: 1)
                }
        }
        .onAppear {
            quantity = request.quantity
        }
    }
}

// MARK: - Stop Loss & Target Card

private struct StopLossTargetCard: View {
    let request: TradeExecutionRequest
    let onStopLossChange: (Double?) -> Void
    let onTargetChange: (Double?) -> Void

    @State private var stopLossText: String = ""
    @State private var targetText: String = ""
    @State private var isStopLossEnabled: Bool = false
    @State private var isTargetEnabled: Bool = false

    private var suggestedSL: Double {
        // Suggest 20% below entry for buy, 20% above for sell
        if request.direction == .buy {
            return request.price * 0.80
        } else {
            return request.price * 1.20
        }
    }

    private var suggestedTarget: Double {
        // Suggest 30% above entry for buy, 30% below for sell
        if request.direction == .buy {
            return request.price * 1.30
        } else {
            return request.price * 0.70
        }
    }

    var body: some View {
        VStack(spacing: 16) {
            HStack {
                Text(L.paperTradeStopLossTarget)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Theme.textSecondary)

                Spacer()

                Text("Optional")
                    .font(.system(size: 11))
                    .foregroundColor(Theme.textMuted)
            }

            // Stop Loss Row
            VStack(spacing: 8) {
                HStack {
                    Toggle(isOn: $isStopLossEnabled) {
                        HStack(spacing: 8) {
                            Image(systemName: "arrow.down.circle.fill")
                                .foregroundColor(Theme.loss)
                                .font(.system(size: 16))

                            Text(L.paperTradeStopLoss)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(Theme.textPrimary)
                        }
                    }
                    .toggleStyle(SwitchToggleStyle(tint: Theme.loss))
                    .onChange(of: isStopLossEnabled) { _, enabled in
                        if enabled && stopLossText.isEmpty {
                            stopLossText = String(format: "%.2f", suggestedSL)
                            onStopLossChange(suggestedSL)
                        } else if !enabled {
                            onStopLossChange(nil)
                        }
                    }
                }

                if isStopLossEnabled {
                    HStack(spacing: 12) {
                        Text("₹")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(Theme.textMuted)

                        TextField("0.00", text: $stopLossText)
                            .font(.system(size: 18, weight: .semibold, design: .monospaced))
                            .foregroundColor(Theme.loss)
                            .keyboardType(.decimalPad)
                            .onChange(of: stopLossText) { _, newValue in
                                if let value = Double(newValue), value > 0 {
                                    onStopLossChange(value)
                                }
                            }

                        Spacer()

                        if let slPnL = request.stopLossPnL {
                            Text(formatPnL(slPnL))
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(slPnL >= 0 ? Theme.profit : Theme.loss)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background {
                                    Capsule()
                                        .fill((slPnL >= 0 ? Theme.profit : Theme.loss).opacity(0.15))
                                }
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background {
                        RoundedRectangle(cornerRadius: 10)
                            .fill(Theme.card)
                    }
                }
            }

            // Target Row
            VStack(spacing: 8) {
                HStack {
                    Toggle(isOn: $isTargetEnabled) {
                        HStack(spacing: 8) {
                            Image(systemName: "arrow.up.circle.fill")
                                .foregroundColor(Theme.profit)
                                .font(.system(size: 16))

                            Text(L.paperTradeTarget)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(Theme.textPrimary)
                        }
                    }
                    .toggleStyle(SwitchToggleStyle(tint: Theme.profit))
                    .onChange(of: isTargetEnabled) { _, enabled in
                        if enabled && targetText.isEmpty {
                            targetText = String(format: "%.2f", suggestedTarget)
                            onTargetChange(suggestedTarget)
                        } else if !enabled {
                            onTargetChange(nil)
                        }
                    }
                }

                if isTargetEnabled {
                    HStack(spacing: 12) {
                        Text("₹")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(Theme.textMuted)

                        TextField("0.00", text: $targetText)
                            .font(.system(size: 18, weight: .semibold, design: .monospaced))
                            .foregroundColor(Theme.profit)
                            .keyboardType(.decimalPad)
                            .onChange(of: targetText) { _, newValue in
                                if let value = Double(newValue), value > 0 {
                                    onTargetChange(value)
                                }
                            }

                        Spacer()

                        if let tgtPnL = request.targetPnL {
                            Text(formatPnL(tgtPnL))
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(tgtPnL >= 0 ? Theme.profit : Theme.loss)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background {
                                    Capsule()
                                        .fill((tgtPnL >= 0 ? Theme.profit : Theme.loss).opacity(0.15))
                                }
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background {
                        RoundedRectangle(cornerRadius: 10)
                            .fill(Theme.card)
                    }
                }
            }

            // Risk:Reward Ratio
            if isStopLossEnabled && isTargetEnabled, let rr = request.riskRewardRatio {
                HStack {
                    Text("Risk:Reward")
                        .font(.system(size: 12))
                        .foregroundColor(Theme.textMuted)

                    Spacer()

                    Text(String(format: "1:%.1f", rr))
                        .font(.system(size: 14, weight: .bold, design: .monospaced))
                        .foregroundColor(rr >= 2 ? Theme.profit : (rr >= 1 ? Theme.accentOrange : Theme.loss))
                }
                .padding(.top, 4)
            }
        }
        .padding(20)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
                .overlay {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.white.opacity(0.05), lineWidth: 1)
                }
        }
        .onAppear {
            // Initialize from request if already set
            if let sl = request.stopLoss {
                isStopLossEnabled = true
                stopLossText = String(format: "%.2f", sl)
            }
            if let tgt = request.target {
                isTargetEnabled = true
                targetText = String(format: "%.2f", tgt)
            }
        }
    }

    private func formatPnL(_ value: Double) -> String {
        let prefix = value >= 0 ? "+" : ""
        if abs(value) >= 1_00_000 {
            return String(format: "%@₹%.1fL", prefix, value / 1_00_000)
        } else if abs(value) >= 1000 {
            return String(format: "%@₹%.1fK", prefix, value / 1000)
        }
        return String(format: "%@₹%.0f", prefix, value)
    }
}

// MARK: - Order Summary Card

private struct OrderSummaryCard: View {
    let request: TradeExecutionRequest
    let availableBalance: Double
    let availableMargin: Double

    var body: some View {
        VStack(spacing: 16) {
            Text("Order Summary")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Theme.textSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            VStack(spacing: 12) {
                SummaryRow(label: "Total Value", value: formatCurrency(request.totalValue), isBold: true)

                if request.direction == .sell {
                    SummaryRow(label: "Margin Required", value: formatCurrency(request.marginRequired))
                    SummaryRow(label: "Available Margin", value: formatCurrency(availableMargin),
                              color: availableMargin >= request.marginRequired ? Theme.textSecondary : Theme.loss)
                } else {
                    SummaryRow(label: "Available Balance", value: formatCurrency(availableBalance),
                              color: availableBalance >= request.totalValue ? Theme.textSecondary : Theme.loss)
                }

                Divider()
                    .background(Theme.card)

                if request.direction == .buy {
                    SummaryRow(label: "Balance After Trade",
                              value: formatCurrency(availableBalance - request.totalValue),
                              color: Theme.accentBlue)
                } else {
                    SummaryRow(label: "Margin After Trade",
                              value: formatCurrency(availableMargin - request.marginRequired),
                              color: Theme.accentBlue)
                }
            }
        }
        .padding(20)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
                .overlay {
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.white.opacity(0.05), lineWidth: 1)
                }
        }
    }

    private func formatCurrency(_ value: Double) -> String {
        if abs(value) >= 1_00_000 {
            return String(format: "₹%.2fL", value / 1_00_000)
        }
        return String(format: "₹%.0f", value)
    }
}

private struct SummaryRow: View {
    let label: String
    let value: String
    var isBold: Bool = false
    var color: Color = Theme.textSecondary

    var body: some View {
        HStack {
            Text(label)
                .font(.system(size: 13, weight: isBold ? .semibold : .regular))
                .foregroundColor(Theme.textMuted)

            Spacer()

            Text(value)
                .font(.system(size: 14, weight: isBold ? .bold : .semibold, design: .monospaced))
                .foregroundColor(isBold ? .white : color)
        }
    }
}

// MARK: - Execute Button

private struct ExecuteButton: View {
    let request: TradeExecutionRequest
    let canExecute: (canExecute: Bool, reason: String?)
    let onExecute: () -> Void

    var body: some View {
        VStack(spacing: 8) {
            Button {
                let impact = UIImpactFeedbackGenerator(style: .heavy)
                impact.impactOccurred()
                onExecute()
            } label: {
                HStack(spacing: 12) {
                    Image(systemName: request.direction.icon)
                        .font(.system(size: 20, weight: .bold))

                    Text(request.direction == .buy ? L.paperTradeBuy : L.paperTradeSell)
                        .font(.system(size: 18, weight: .bold))
                }
                .foregroundColor(Theme.textPrimary)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 18)
                .background {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(canExecute.canExecute ?
                              (request.direction == .buy ? Theme.profit : Theme.loss) :
                              Theme.textDisabled)
                }
            }
            .disabled(!canExecute.canExecute)

            if let reason = canExecute.reason {
                Text(reason)
                    .font(.system(size: 12))
                    .foregroundColor(Theme.loss)
            }
        }
    }
}

// MARK: - Square Off Sheet

struct SquareOffSheet: View {
    @ObservedObject var viewModel: PaperTradingViewModel
    let position: PaperPosition
    @Environment(\.dismiss) private var dismiss

    @State private var quantity: Int = 1
    @State private var currentLTP: Double = 0

    var body: some View {
        NavigationView {
            ZStack {
                Theme.backgroundGradient.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 20) {
                        // Position Info
                        PositionInfoCard(position: position)

                        // Quantity Selector
                        VStack(spacing: 16) {
                            HStack {
                                Text("Quantity to Close")
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundColor(Theme.textSecondary)

                                Spacer()

                                Text("\(quantity) of \(position.quantity) lots")
                                    .font(.system(size: 12))
                                    .foregroundColor(Theme.textMuted)
                            }

                            HStack(spacing: 20) {
                                Button {
                                    if quantity > 1 { quantity -= 1 }
                                } label: {
                                    Image(systemName: "minus.circle.fill")
                                        .font(.system(size: 36))
                                        .foregroundColor(quantity > 1 ? Theme.loss : Theme.textDisabled)
                                }
                                .disabled(quantity <= 1)

                                Text("\(quantity)")
                                    .font(.system(size: 48, weight: .bold, design: .rounded))
                                    .foregroundColor(Theme.textPrimary)
                                    .frame(minWidth: 80)

                                Button {
                                    if quantity < position.quantity { quantity += 1 }
                                } label: {
                                    Image(systemName: "plus.circle.fill")
                                        .font(.system(size: 36))
                                        .foregroundColor(quantity < position.quantity ? Theme.profit : Theme.textDisabled)
                                }
                                .disabled(quantity >= position.quantity)
                            }

                            // Close all button
                            if position.quantity > 1 {
                                Button {
                                    quantity = position.quantity
                                } label: {
                                    Text("Close All")
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundColor(Theme.accentOrange)
                                        .padding(.horizontal, 20)
                                        .padding(.vertical, 10)
                                        .background {
                                            Capsule()
                                                .fill(Theme.accentOrange.opacity(0.15))
                                        }
                                }
                            }
                        }
                        .padding(20)
                        .background {
                            RoundedRectangle(cornerRadius: 16)
                                .fill(Theme.surface)
                        }

                        // P&L Preview
                        PnLPreviewCard(
                            position: position,
                            quantity: quantity,
                            currentLTP: currentLTP
                        )

                        // Square Off Button
                        Button {
                            let impact = UIImpactFeedbackGenerator(style: .heavy)
                            impact.impactOccurred()
                            viewModel.squareOff(position: position, currentLTP: currentLTP, quantity: quantity)
                            dismiss()
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.system(size: 20, weight: .bold))

                                Text(L.paperTradeSquareOff)
                                    .font(.system(size: 18, weight: .bold))
                            }
                            .foregroundColor(Theme.textPrimary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 18)
                            .background {
                                RoundedRectangle(cornerRadius: 16)
                                    .fill(Theme.accentOrange)
                            }
                        }
                    }
                    .padding(20)
                }
            }
            .navigationTitle(L.paperTradeSquareOff)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(Theme.textMuted)
                    }
                }
            }
        }
        
        .onAppear {
            quantity = position.quantity
            currentLTP = position.currentLTP
        }
    }
}

private struct PositionInfoCard: View {
    let position: PaperPosition

    var body: some View {
        VStack(spacing: 12) {
            HStack {
                Text(position.index)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(Theme.accentBlue)

                Spacer()

                Text(position.direction.displayName)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(position.direction.color)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background {
                        Capsule()
                            .fill(position.direction.color.opacity(0.15))
                    }
            }

            HStack(alignment: .bottom) {
                Text(position.optionSymbol)
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Spacer()

                VStack(alignment: .trailing, spacing: 2) {
                    Text("Current LTP")
                        .font(.system(size: 10))
                        .foregroundColor(Theme.textMuted)

                    Text(String(format: "₹%.2f", position.currentLTP))
                        .font(.system(size: 18, weight: .bold, design: .monospaced))
                        .foregroundColor(Theme.accentGreen)
                }
            }
        }
        .padding(16)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
        }
    }
}

private struct PnLPreviewCard: View {
    let position: PaperPosition
    let quantity: Int
    let currentLTP: Double

    private var expectedPnL: Double {
        (currentLTP - position.averageEntryPrice) * Double(quantity) * Double(position.lotSize) * position.direction.multiplier
    }

    private var expectedPnLPercent: Double {
        guard position.averageEntryPrice > 0 else { return 0 }
        let entryValue = position.averageEntryPrice * Double(quantity) * Double(position.lotSize)
        return (expectedPnL / entryValue) * 100
    }

    var body: some View {
        VStack(spacing: 12) {
            Text("Expected P&L")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Theme.textSecondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            HStack {
                Text(formatPnL(expectedPnL))
                    .font(.system(size: 32, weight: .bold, design: .rounded))
                    .foregroundColor(expectedPnL >= 0 ? Theme.profit : Theme.loss)

                Spacer()

                Text(formatPercent(expectedPnLPercent))
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(expectedPnL >= 0 ? Theme.profit : Theme.loss)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background {
                        Capsule()
                            .fill((expectedPnL >= 0 ? Theme.profit : Theme.loss).opacity(0.15))
                    }
            }

            HStack {
                VStack(alignment: .leading) {
                    Text("Entry")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)
                    Text(String(format: "₹%.2f", position.averageEntryPrice))
                        .font(.system(size: 14, weight: .semibold, design: .monospaced))
                        .foregroundColor(Theme.textSecondary)
                }

                Spacer()

                Image(systemName: "arrow.right")
                    .foregroundColor(Theme.textMuted)

                Spacer()

                VStack(alignment: .trailing) {
                    Text("Exit")
                        .font(.system(size: 11))
                        .foregroundColor(Theme.textMuted)
                    Text(String(format: "₹%.2f", currentLTP))
                        .font(.system(size: 14, weight: .semibold, design: .monospaced))
                        .foregroundColor(Theme.accentGreen)
                }
            }
        }
        .padding(20)
        .background {
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
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
        return String(format: "%@%.1f%%", prefix, value)
    }
}

// MARK: - Preview

#Preview {
    TradeExecutionSheet(viewModel: PaperTradingViewModel())
}
