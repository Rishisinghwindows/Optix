import SwiftUI

// MARK: - Leg Row View

struct LegRowView: View {
    @Binding var leg: StrategyLeg
    let availableStrikes: [Double]
    let onDelete: () -> Void

    @State private var showStrikePicker = false
    @State private var premiumText: String = ""

    var body: some View {
        VStack(spacing: 12) {
            // Main row
            HStack(spacing: 12) {
                // Position toggle (BUY/SELL)
                PositionToggle(position: $leg.position)

                // Quantity
                QuantityControl(quantity: $leg.quantity)

                // Strike picker
                StrikeButton(
                    strike: leg.strikePrice,
                    action: { showStrikePicker = true }
                )

                // Option type toggle (CE/PE)
                OptionTypeToggle(optionType: $leg.optionType)

                // Delete button
                Button(action: onDelete) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title3)
                        .foregroundColor(Theme.textMuted)
                }
                .buttonStyle(.plain)
            }

            // Premium and Greeks row
            HStack(spacing: 16) {
                // Premium
                VStack(alignment: .leading, spacing: 2) {
                    Text("Premium")
                        .font(.caption2)
                        .foregroundColor(Theme.textMuted)

                    HStack(spacing: 4) {
                        Text("₹")
                            .font(.caption)
                            .foregroundColor(Theme.textSecondary)
                        TextField("0.00", text: $premiumText)
                            .font(.system(.subheadline, design: .monospaced))
                            .foregroundColor(Theme.textPrimary)
                            .keyboardType(.decimalPad)
                            .frame(width: 60)
                            .onAppear {
                                premiumText = String(format: "%.2f", leg.premium)
                            }
                            .onChange(of: premiumText) { _, newValue in
                                if let value = Double(newValue) {
                                    leg.premium = value
                                }
                            }
                    }
                }

                Spacer()

                // Greeks display
                GreeksDisplay(leg: leg)
            }
            .padding(.horizontal, 4)
        }
        .padding(12)
        .background(Theme.surface)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(leg.position.color.opacity(0.3), lineWidth: 1)
        )
        .sheet(isPresented: $showStrikePicker) {
            StrikePickerSheet(
                selectedStrike: $leg.strikePrice,
                availableStrikes: availableStrikes
            )
            .presentationDetents([.medium])
        }
    }
}

// MARK: - Position Toggle

private struct PositionToggle: View {
    @Binding var position: LegPosition

    var body: some View {
        HStack(spacing: 0) {
            ForEach(LegPosition.allCases, id: \.self) { pos in
                Button(action: {
                    withAnimation(.spring(response: 0.25, dampingFraction: 0.7)) {
                        position = pos
                    }
                }) {
                    Text(pos.rawValue)
                        .font(.caption)
                        .fontWeight(.semibold)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(position == pos ? pos.color : Color.clear)
                        .foregroundColor(position == pos ? .white : Theme.textSecondary)
                }
            }
        }
        .background(Theme.card)
        .cornerRadius(8)
    }
}

// MARK: - Quantity Control

private struct QuantityControl: View {
    @Binding var quantity: Int

    var body: some View {
        HStack(spacing: 4) {
            Button(action: {
                if quantity > 1 {
                    quantity -= 1
                }
            }) {
                Image(systemName: "minus")
                    .font(.caption)
                    .foregroundColor(Theme.textSecondary)
                    .frame(width: 24, height: 24)
                    .background(Theme.card)
                    .cornerRadius(6)
            }
            .buttonStyle(.plain)

            Text("\(quantity)")
                .font(.system(.subheadline, design: .monospaced))
                .fontWeight(.medium)
                .foregroundColor(Theme.textPrimary)
                .frame(minWidth: 24)

            Button(action: {
                quantity += 1
            }) {
                Image(systemName: "plus")
                    .font(.caption)
                    .foregroundColor(Theme.textSecondary)
                    .frame(width: 24, height: 24)
                    .background(Theme.card)
                    .cornerRadius(6)
            }
            .buttonStyle(.plain)
        }
    }
}

// MARK: - Strike Button

private struct StrikeButton: View {
    let strike: Double
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                Text(String(format: "%.0f", strike))
                    .font(.system(.subheadline, design: .monospaced))
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.textPrimary)

                Image(systemName: "chevron.up.chevron.down")
                    .font(.caption2)
                    .foregroundColor(Theme.textMuted)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(Theme.card)
            .cornerRadius(8)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Option Type Toggle

private struct OptionTypeToggle: View {
    @Binding var optionType: OptionType

    var body: some View {
        HStack(spacing: 0) {
            ForEach(OptionType.allCases, id: \.self) { type in
                Button(action: {
                    withAnimation(.spring(response: 0.25, dampingFraction: 0.7)) {
                        optionType = type
                    }
                }) {
                    Text(type.shortName)
                        .font(.caption)
                        .fontWeight(.semibold)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(
                            optionType == type ?
                                (type == .call ? Theme.profit : Theme.loss) : Color.clear
                        )
                        .foregroundColor(
                            optionType == type ? .white :
                                (type == .call ? Theme.profit : Theme.loss)
                        )
                }
            }
        }
        .background(Theme.card)
        .cornerRadius(8)
    }
}

// MARK: - Greeks Display

private struct GreeksDisplay: View {
    let leg: StrategyLeg

    var body: some View {
        HStack(spacing: 12) {
            LegGreekItem(label: "Δ", value: leg.delta, format: "%.2f")
            LegGreekItem(label: "Γ", value: leg.gamma, format: "%.4f")
            LegGreekItem(label: "Θ", value: leg.theta, format: "%.2f")
            LegGreekItem(label: "V", value: leg.vega, format: "%.2f")
        }
    }
}

private struct LegGreekItem: View {
    let label: String
    let value: Double
    let format: String

    var body: some View {
        VStack(spacing: 2) {
            Text(label)
                .font(.caption2)
                .foregroundColor(Theme.textMuted)
            Text(String(format: format, value))
                .font(.system(.caption2, design: .monospaced))
                .foregroundColor(Theme.textSecondary)
        }
    }
}

// MARK: - Strike Picker Sheet

private struct StrikePickerSheet: View {
    @Binding var selectedStrike: Double
    let availableStrikes: [Double]
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            List {
                ForEach(availableStrikes, id: \.self) { strike in
                    Button(action: {
                        selectedStrike = strike
                        dismiss()
                    }) {
                        HStack {
                            Text(String(format: "%.0f", strike))
                                .font(.system(.body, design: .monospaced))
                                .foregroundColor(Theme.textPrimary)

                            Spacer()

                            if selectedStrike == strike {
                                Image(systemName: "checkmark")
                                    .foregroundColor(Theme.accentGreen)
                            }
                        }
                    }
                }
            }
            .listStyle(.plain)
            .navigationTitle("Select Strike")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
    }
}

// MARK: - Preview

#Preview {
    VStack {
        LegRowView(
            leg: .constant(StrategyLeg(
                strikePrice: 24000,
                optionType: .call,
                position: .buy,
                quantity: 1,
                premium: 150.50,
                delta: 0.55,
                gamma: 0.0012,
                theta: -5.20,
                vega: 8.50
            )),
            availableStrikes: Array(stride(from: 23000.0, through: 25000.0, by: 50)),
            onDelete: {}
        )

        LegRowView(
            leg: .constant(StrategyLeg(
                strikePrice: 24200,
                optionType: .call,
                position: .sell,
                quantity: 1,
                premium: 85.25,
                delta: 0.35,
                gamma: 0.0010,
                theta: -4.50,
                vega: 7.20
            )),
            availableStrikes: Array(stride(from: 23000.0, through: 25000.0, by: 50)),
            onDelete: {}
        )
    }
    .padding()
    .background(Theme.background)
    
}
