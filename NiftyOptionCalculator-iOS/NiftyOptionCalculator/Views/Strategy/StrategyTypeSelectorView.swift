import SwiftUI

// MARK: - Strategy Type Selector View

struct StrategyTypeSelectorView: View {
    @Binding var selectedType: StrategyType
    @Binding var isPresented: Bool

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 24) {
                    ForEach(StrategyCategory.allCases, id: \.self) { category in
                        CategorySection(
                            category: category,
                            selectedType: $selectedType,
                            onSelect: { type in
                                selectedType = type
                                isPresented = false
                            }
                        )
                    }
                }
                .padding()
            }
            .background(Theme.background)
            .navigationTitle("Select Strategy")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Cancel") {
                        isPresented = false
                    }
                    .foregroundColor(Theme.accentBlue)
                }
            }
        }
    }
}

// MARK: - Category Section

private struct CategorySection: View {
    let category: StrategyCategory
    @Binding var selectedType: StrategyType
    let onSelect: (StrategyType) -> Void

    private var strategiesInCategory: [StrategyType] {
        StrategyType.allCases.filter { $0.category == category }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Category header
            HStack(spacing: 8) {
                Image(systemName: category.icon)
                    .font(.subheadline)
                    .foregroundColor(Theme.accentBlue)

                Text(category.rawValue)
                    .font(.headline)
                    .foregroundColor(Theme.textPrimary)
            }
            .padding(.horizontal, 4)

            // Strategy cards grid
            LazyVGrid(
                columns: [GridItem(.flexible()), GridItem(.flexible())],
                spacing: 12
            ) {
                ForEach(strategiesInCategory, id: \.self) { type in
                    StrategyCard(
                        type: type,
                        isSelected: selectedType == type,
                        onTap: { onSelect(type) }
                    )
                }
            }
        }
    }
}

// MARK: - Strategy Card

private struct StrategyCard: View {
    let type: StrategyType
    let isSelected: Bool
    let onTap: () -> Void

    var body: some View {
        Button(action: {
            let generator = UIImpactFeedbackGenerator(style: .light)
            generator.impactOccurred()
            onTap()
        }) {
            VStack(alignment: .leading, spacing: 8) {
                // Header
                HStack {
                    Image(systemName: type.icon)
                        .font(.title3)
                        .foregroundColor(iconColor)

                    Spacer()

                    // Risk indicator
                    RiskBadge(profile: type.riskProfile)
                }

                // Name
                Text(type.rawValue)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(Theme.textPrimary)
                    .lineLimit(1)

                // Description
                Text(type.description)
                    .font(.caption2)
                    .foregroundColor(Theme.textSecondary)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)

                Spacer(minLength: 0)

                // Leg count
                HStack {
                    Text("\(type.legCount) leg\(type.legCount > 1 ? "s" : "")")
                        .font(.caption2)
                        .foregroundColor(Theme.textMuted)

                    Spacer()

                    // Outlook
                    Text(type.riskProfile.outlook.rawValue)
                        .font(.caption2)
                        .fontWeight(.medium)
                        .foregroundColor(outlookColor)
                }
            }
            .padding(12)
            .frame(height: 140)
            .background(isSelected ? Theme.accentBlue.opacity(0.1) : Theme.card)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isSelected ? Theme.accentBlue : Theme.card.opacity(0.5), lineWidth: isSelected ? 2 : 1)
            )
        }
        .buttonStyle(.plain)
    }

    private var iconColor: Color {
        switch type.riskProfile.outlook {
        case .bullish, .bullishNeutral:
            return Theme.profit
        case .bearish, .bearishNeutral:
            return Theme.loss
        case .neutral:
            return Theme.accentBlue
        case .volatility:
            return Theme.accentPurple
        case .directional:
            return Theme.accentOrange
        }
    }

    private var outlookColor: Color {
        switch type.riskProfile.outlook {
        case .bullish, .bullishNeutral:
            return Theme.profit
        case .bearish, .bearishNeutral:
            return Theme.loss
        case .neutral:
            return Theme.accentBlue
        case .volatility:
            return Theme.accentPurple
        case .directional:
            return Theme.accentOrange
        }
    }
}

// MARK: - Risk Badge

private struct RiskBadge: View {
    let profile: RiskProfile

    var body: some View {
        HStack(spacing: 2) {
            Circle()
                .fill(profile.maxRisk == .limited ? Theme.profit : Theme.loss)
                .frame(width: 6, height: 6)
        }
    }
}

// MARK: - Preview

#Preview {
    StrategyTypeSelectorView(
        selectedType: .constant(.bullCallSpread),
        isPresented: .constant(true)
    )
    
}
