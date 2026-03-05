import SwiftUI

// MARK: - Legs Builder Card

struct LegsBuilderCard: View {
    @ObservedObject var viewModel: StrategyBuilderViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            // Header with strategy type selector
            HStack {
                Button(action: {
                    viewModel.showStrategySelector = true
                }) {
                    HStack(spacing: 8) {
                        Image(systemName: viewModel.selectedStrategyType.icon)
                            .font(.title3)

                        VStack(alignment: .leading, spacing: 2) {
                            Text(viewModel.selectedStrategyType.rawValue)
                                .font(.headline)
                                .foregroundColor(Theme.textPrimary)

                            Text(viewModel.selectedStrategyType.riskProfile.outlook.rawValue)
                                .font(.caption)
                                .foregroundColor(Theme.textSecondary)
                        }

                        Image(systemName: "chevron.down")
                            .font(.caption)
                            .foregroundColor(Theme.textMuted)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Theme.surface)
                    .cornerRadius(10)
                }
                .buttonStyle(.plain)

                Spacer()

                // Actions
                HStack(spacing: 8) {
                    Button(action: {
                        viewModel.resetToTemplate()
                    }) {
                        Image(systemName: "arrow.counterclockwise")
                            .font(.body)
                            .foregroundColor(Theme.textSecondary)
                            .frame(width: 36, height: 36)
                            .background(Theme.surface)
                            .cornerRadius(8)
                    }
                    .buttonStyle(.plain)

                    Button(action: {
                        viewModel.addLeg()
                    }) {
                        Image(systemName: "plus")
                            .font(.body)
                            .fontWeight(.semibold)
                            .foregroundColor(Theme.textPrimary)
                            .frame(width: 36, height: 36)
                            .background(Theme.accentBlue)
                            .cornerRadius(8)
                    }
                    .buttonStyle(.plain)
                }
            }

            // Strategy description
            Text(viewModel.selectedStrategyType.description)
                .font(.caption)
                .foregroundColor(Theme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)

            // Legs list
            if viewModel.strategy.legs.isEmpty {
                EmptyLegsView(onAddLeg: { viewModel.addLeg() })
            } else {
                VStack(spacing: 10) {
                    ForEach(Array(viewModel.strategy.legs.enumerated()), id: \.element.id) { index, _ in
                        LegRowView(
                            leg: Binding(
                                get: { viewModel.strategy.legs[index] },
                                set: { viewModel.strategy.legs[index] = $0 }
                            ),
                            availableStrikes: viewModel.availableStrikesForPicker,
                            onDelete: {
                                withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                    viewModel.removeLeg(at: index)
                                }
                            }
                        )
                    }
                }
            }

            // Strategy summary
            if !viewModel.strategy.legs.isEmpty {
                Divider()
                    .background(Theme.textMuted.opacity(0.3))

                HStack {
                    Text(viewModel.strategyDescription)
                        .font(.caption)
                        .foregroundColor(Theme.textMuted)
                        .lineLimit(1)

                    Spacer()

                    Text(viewModel.strategy.displayNetPremium)
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(viewModel.strategy.isDebit ? Theme.loss : Theme.profit)
                }
            }
        }
        .padding(16)
        .background(Theme.card)
        .cornerRadius(16)
    }
}

// MARK: - Empty Legs View

private struct EmptyLegsView: View {
    let onAddLeg: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "rectangle.stack.badge.plus")
                .font(.system(size: 32))
                .foregroundColor(Theme.textMuted)

            Text("No legs added")
                .font(.subheadline)
                .foregroundColor(Theme.textSecondary)

            Text("Add legs manually or select a strategy template")
                .font(.caption)
                .foregroundColor(Theme.textMuted)
                .multilineTextAlignment(.center)

            Button(action: onAddLeg) {
                HStack(spacing: 6) {
                    Image(systemName: "plus")
                    Text("Add First Leg")
                }
                .font(.subheadline)
                .fontWeight(.medium)
                .foregroundColor(Theme.textPrimary)
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(Theme.accentBlue)
                .cornerRadius(10)
            }
            .buttonStyle(.plain)
            .padding(.top, 4)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
    }
}

// MARK: - Preview

#Preview {
    let viewModel = StrategyBuilderViewModel(
        spotPrice: 24000,
        strikeInterval: 50,
        lotSize: 25,
        expiryDate: Date().addingTimeInterval(7 * 24 * 60 * 60),
        optionChain: []
    )

    return LegsBuilderCard(viewModel: viewModel)
        .padding()
        .background(Theme.background)
        
}
