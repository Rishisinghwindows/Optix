import SwiftUI

// MARK: - Education Tab View

struct EducationTabView: View {
    @State private var selectedCategory: EducationCategory = .basics
    @State private var searchText = ""

    private let provider = EducationDataProvider.shared

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()

            VStack(spacing: 0) {
                // Category Selector
                categorySelector

                // Content
                ScrollView {
                    LazyVStack(spacing: 16) {
                        switch selectedCategory {
                        case .basics:
                            basicsContent
                        case .greeks:
                            greeksContent
                        case .strategies:
                            strategiesContent
                        }
                    }
                    .padding(16)
                }
            }
        }
        .navigationTitle(L.educationTitle)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden, for: .tabBar)
    }

    // MARK: - Category Selector

    private var categorySelector: some View {
        HStack(spacing: 0) {
            ForEach(EducationCategory.allCases) { category in
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        selectedCategory = category
                    }
                } label: {
                    VStack(spacing: 6) {
                        Image(systemName: category.icon)
                            .font(.system(size: 18, weight: .semibold))
                        Text(categoryDisplayName(category))
                            .font(.system(size: 12, weight: .semibold))
                    }
                    .foregroundColor(selectedCategory == category ? .white : Theme.textSecondary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background {
                        if selectedCategory == category {
                            RoundedRectangle(cornerRadius: 12)
                                .fill(category.color)
                                .padding(.horizontal, 4)
                                .padding(.vertical, 4)
                        }
                    }
                }
            }
        }
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }

    private func categoryDisplayName(_ category: EducationCategory) -> String {
        switch category {
        case .basics: return L.educationBasics
        case .greeks: return L.educationGreeks
        case .strategies: return L.educationStrategies
        }
    }

    // MARK: - Basics Content

    private var basicsContent: some View {
        ForEach(provider.basicsLessons) { lesson in
            NavigationLink(destination: LessonDetailView(lesson: lesson)) {
                LessonCard(lesson: lesson)
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - Greeks Content

    private var greeksContent: some View {
        ForEach(provider.greeksInfo) { greek in
            NavigationLink(destination: GreekDetailView(greek: greek)) {
                GreekCard(greek: greek)
            }
            .buttonStyle(.plain)
        }
    }

    // MARK: - Strategies Content

    private var strategiesContent: some View {
        ForEach(provider.strategies) { strategy in
            NavigationLink(destination: StrategyDetailView(strategy: strategy)) {
                EducationStrategyCard(strategy: strategy)
            }
            .buttonStyle(.plain)
        }
    }
}

// MARK: - Lesson Card

struct LessonCard: View {
    let lesson: Lesson

    var body: some View {
        HStack(spacing: 14) {
            // Icon
            Image(systemName: lesson.icon)
                .font(.system(size: 22, weight: .semibold))
                .foregroundColor(.white)
                .frame(width: 50, height: 50)
                .background(
                    LinearGradient(
                        colors: [Theme.accentBlue, Theme.accentBlue.opacity(0.7)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .clipShape(RoundedRectangle(cornerRadius: 12))

            // Content
            VStack(alignment: .leading, spacing: 4) {
                Text(lesson.title)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)

                Text(lesson.subtitle)
                    .font(.system(size: 13))
                    .foregroundColor(Theme.textSecondary)
                    .lineLimit(1)

                HStack(spacing: 8) {
                    Label(lesson.duration, systemImage: "clock")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(Theme.textMuted)

                    if lesson.quiz != nil {
                        Label(L.educationHasQuiz, systemImage: "checkmark.circle")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(Theme.accentGreen)
                    }
                }
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Theme.textMuted)
        }
        .padding(14)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Theme.border, lineWidth: 1)
        )
    }
}

// MARK: - Greek Card

struct GreekCard: View {
    let greek: GreekInfo

    var body: some View {
        HStack(spacing: 14) {
            // Symbol
            Text(greek.symbol)
                .font(.system(size: 28, weight: .bold, design: .serif))
                .foregroundColor(.white)
                .frame(width: 50, height: 50)
                .background(
                    LinearGradient(
                        colors: [greek.color, greek.color.opacity(0.7)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .clipShape(RoundedRectangle(cornerRadius: 12))

            // Content
            VStack(alignment: .leading, spacing: 4) {
                Text(greek.name)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Text(greek.shortDescription)
                    .font(.system(size: 13))
                    .foregroundColor(Theme.textSecondary)
                    .lineLimit(2)
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(Theme.textMuted)
        }
        .padding(14)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Theme.border, lineWidth: 1)
        )
    }
}

// MARK: - Strategy Card

struct EducationStrategyCard: View {
    let strategy: OptionStrategy

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Header
            HStack {
                Text(strategy.name)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Spacer()

                // Risk Level Badge
                Text(strategy.riskLevel.rawValue)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(strategy.riskLevel.color)
                    .clipShape(Capsule())
            }

            // Tags
            HStack(spacing: 8) {
                StrategyTag(text: strategy.type.rawValue, color: typeColor(strategy.type))
                StrategyTag(text: strategy.outlook.rawValue, color: Theme.accentPurple)
                StrategyTag(text: "\(strategy.legs.count) Legs", color: Theme.textMuted)
            }

            // Description
            Text(strategy.description)
                .font(.system(size: 13))
                .foregroundColor(Theme.textSecondary)
                .lineLimit(2)

            // Arrow
            HStack {
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(Theme.textMuted)
            }
        }
        .padding(14)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Theme.border, lineWidth: 1)
        )
    }

    private func typeColor(_ type: EducationStrategyType) -> Color {
        switch type {
        case .bullish: return Theme.profit
        case .bearish: return Theme.loss
        case .neutral: return Theme.accentBlue
        case .volatile: return Theme.accentOrange
        }
    }
}

struct StrategyTag: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.system(size: 10, weight: .semibold))
            .foregroundColor(color)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(color.opacity(0.15))
            .clipShape(Capsule())
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        EducationTabView()
    }
}
