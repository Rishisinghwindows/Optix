import SwiftUI

// MARK: - Greek Detail View

struct GreekDetailView: View {
    let greek: GreekInfo

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                // Header Card
                headerCard

                // What It Measures
                descriptionSection

                // Impact
                impactSection

                // Range
                rangeSection

                // Example
                exampleSection

                // Tips
                tipsSection

                Spacer(minLength: 40)
            }
            .padding(16)
        }
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle(greek.name)
        .navigationBarTitleDisplayMode(.inline)
    }

    // MARK: - Header Card

    private var headerCard: some View {
        HStack(spacing: 16) {
            // Symbol
            Text(greek.symbol)
                .font(.system(size: 48, weight: .bold, design: .serif))
                .foregroundColor(.white)
                .frame(width: 80, height: 80)
                .background(
                    LinearGradient(
                        colors: [greek.color, greek.color.opacity(0.7)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .clipShape(RoundedRectangle(cornerRadius: 20))
                .shadow(color: greek.color.opacity(0.4), radius: 10, y: 5)

            VStack(alignment: .leading, spacing: 6) {
                Text(greek.name)
                    .font(.system(size: 28, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Text(greek.shortDescription)
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textSecondary)
            }

            Spacer()
        }
        .padding(20)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    // MARK: - Description Section

    private var descriptionSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle(L.educationWhatItMeasures)

            Text(greek.fullDescription)
                .font(.system(size: 15))
                .foregroundColor(Theme.textSecondary)
                .lineSpacing(4)
        }
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    // MARK: - Impact Section

    private var impactSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle(L.educationImpact)

            HStack(alignment: .top, spacing: 12) {
                Image(systemName: "bolt.fill")
                    .font(.system(size: 20))
                    .foregroundColor(greek.color)
                    .frame(width: 30)

                Text(greek.impact)
                    .font(.system(size: 15))
                    .foregroundColor(Theme.textSecondary)
                    .lineSpacing(4)
            }
        }
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    // MARK: - Range Section

    private var rangeSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle(L.educationRange)

            Text(greek.range)
                .font(.system(size: 14, design: .monospaced))
                .foregroundColor(Theme.textPrimary)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.background)
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    // MARK: - Example Section

    private var exampleSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: "lightbulb.fill")
                    .foregroundColor(Theme.accentYellow)
                sectionTitle(L.educationExample)
            }

            Text(greek.example)
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)
                .lineSpacing(4)
                .padding(12)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.accentYellow.opacity(0.1))
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    // MARK: - Tips Section

    private var tipsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "star.fill")
                    .foregroundColor(Theme.accentOrange)
                sectionTitle(L.educationProTips)
            }

            VStack(alignment: .leading, spacing: 10) {
                ForEach(greek.tips, id: \.self) { tip in
                    HStack(alignment: .top, spacing: 10) {
                        Text("•")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(greek.color)

                        Text(tip)
                            .font(.system(size: 14))
                            .foregroundColor(Theme.textSecondary)
                    }
                }
            }
        }
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    // MARK: - Helper

    private func sectionTitle(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 16, weight: .bold))
            .foregroundColor(Theme.textPrimary)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        GreekDetailView(greek: EducationDataProvider.shared.greeksInfo[0])
    }
}
