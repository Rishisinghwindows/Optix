import SwiftUI

struct OptionScreenerView: View {
    @ObservedObject var viewModel: OptionScreenerViewModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 16) {
                    // Filter Card
                    FilterSectionCard(viewModel: viewModel)

                    // Result Count
                    HStack {
                        Text("\(viewModel.resultCount) results")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(Theme.textSecondary)
                        Spacer()
                    }
                    .padding(.horizontal, 4)

                    // Results
                    LazyVStack(spacing: 12) {
                        ForEach(viewModel.results) { result in
                            ScreenerResultCard(result: result)
                        }
                    }

                    if viewModel.results.isEmpty {
                        VStack(spacing: 12) {
                            Image(systemName: "magnifyingglass")
                                .font(.system(size: 40))
                                .foregroundColor(Theme.textMuted)
                            Text("No options match your filters")
                                .font(.system(size: 16, weight: .medium))
                                .foregroundColor(Theme.textMuted)
                            Text("Try adjusting your criteria")
                                .font(.system(size: 13))
                                .foregroundColor(Theme.textDisabled)
                        }
                        .padding(.top, 40)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 32)
            }
            .background(Theme.background.ignoresSafeArea())
            .navigationTitle("Option Screener")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 22))
                            .foregroundColor(Theme.textMuted)
                    }
                }
            }
        }
    }
}

// MARK: - Filter Section Card

private struct FilterSectionCard: View {
    @ObservedObject var viewModel: OptionScreenerViewModel

    var body: some View {
        VStack(spacing: 14) {
            // Option Type Picker
            VStack(alignment: .leading, spacing: 6) {
                Text("Option Type")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(Theme.textSecondary)

                Picker("Option Type", selection: $viewModel.filters.optionType) {
                    ForEach(ScreenerOptionType.allCases) { type in
                        Text(type.rawValue).tag(type)
                    }
                }
                .pickerStyle(.segmented)
            }

            // Moneyness Picker
            VStack(alignment: .leading, spacing: 6) {
                Text("Moneyness")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(Theme.textSecondary)

                Picker("Moneyness", selection: $viewModel.filters.moneyness) {
                    ForEach(MoneynessFilter.allCases) { m in
                        Text(m.rawValue).tag(m)
                    }
                }
                .pickerStyle(.segmented)
            }

            Divider()
                .background(Theme.border)

            // Delta Range
            ScreenerRangeRow(
                label: "Delta Range",
                minValue: $viewModel.filters.deltaMin,
                maxValue: $viewModel.filters.deltaMax,
                minPlaceholder: "0.0",
                maxPlaceholder: "1.0",
                formatter: decimalFormatter
            )

            // IV Range
            ScreenerRangeRow(
                label: "IV Range (%)",
                minValue: $viewModel.filters.ivMin,
                maxValue: $viewModel.filters.ivMax,
                minPlaceholder: "0",
                maxPlaceholder: "200",
                formatter: percentFormatter
            )

            // Premium Range
            ScreenerRangeRow(
                label: "Premium Range",
                minValue: $viewModel.filters.premiumMin,
                maxValue: $viewModel.filters.premiumMax,
                minPlaceholder: "0",
                maxPlaceholder: "100000",
                formatter: priceFormatter
            )

            Divider()
                .background(Theme.border)

            // Min OI and Volume
            HStack(spacing: 12) {
                ScreenerIntField(
                    label: "Min OI",
                    value: $viewModel.filters.oiMin,
                    placeholder: "0"
                )

                ScreenerIntField(
                    label: "Min Volume",
                    value: $viewModel.filters.volumeMin,
                    placeholder: "0"
                )
            }

            Divider()
                .background(Theme.border)

            // Sort By
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Sort By")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Theme.textSecondary)

                    Picker("Sort By", selection: $viewModel.filters.sortBy) {
                        ForEach(ScreenerSortBy.allCases) { s in
                            Text(s.rawValue).tag(s)
                        }
                    }
                    .pickerStyle(.menu)
                    .tint(Theme.accentBlue)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 4) {
                    Text("Order")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Theme.textSecondary)

                    Button {
                        viewModel.filters.sortAscending.toggle()
                        viewModel.updateFilters(viewModel.filters)
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: viewModel.filters.sortAscending ? "arrow.up" : "arrow.down")
                                .font(.system(size: 12, weight: .bold))
                            Text(viewModel.filters.sortAscending ? "Asc" : "Desc")
                                .font(.system(size: 13, weight: .medium))
                        }
                        .foregroundColor(Theme.accentBlue)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Theme.accentBlue.opacity(0.15))
                        .cornerRadius(8)
                    }
                    .buttonStyle(.plain)
                }
            }

            // Reset + Apply
            HStack(spacing: 12) {
                Button {
                    viewModel.resetFilters()
                } label: {
                    Text("Reset")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(Theme.accentRed)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(Theme.accentRed.opacity(0.12))
                        .cornerRadius(10)
                }
                .buttonStyle(.plain)

                Button {
                    viewModel.updateFilters(viewModel.filters)
                } label: {
                    Text("Apply Filters")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(
                            LinearGradient(
                                colors: [Theme.accentBlue, Theme.accentPurple],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(10)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Theme.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Theme.borderLight, lineWidth: 1)
                )
        )
    }

    // MARK: - Formatters

    private var decimalFormatter: NumberFormatter {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.minimumFractionDigits = 0
        f.maximumFractionDigits = 2
        return f
    }

    private var percentFormatter: NumberFormatter {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.minimumFractionDigits = 0
        f.maximumFractionDigits = 1
        return f
    }

    private var priceFormatter: NumberFormatter {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.minimumFractionDigits = 0
        f.maximumFractionDigits = 2
        return f
    }
}

// MARK: - Screener Range Row

private struct ScreenerRangeRow: View {
    let label: String
    @Binding var minValue: Double
    @Binding var maxValue: Double
    let minPlaceholder: String
    let maxPlaceholder: String
    let formatter: NumberFormatter

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(Theme.textSecondary)

            HStack(spacing: 8) {
                TextField(minPlaceholder, value: $minValue, formatter: formatter)
                    .keyboardType(.decimalPad)
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textPrimary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .background(Theme.card)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Theme.borderLight, lineWidth: 1)
                    )

                Text("to")
                    .font(.system(size: 12))
                    .foregroundColor(Theme.textMuted)

                TextField(maxPlaceholder, value: $maxValue, formatter: formatter)
                    .keyboardType(.decimalPad)
                    .font(.system(size: 14))
                    .foregroundColor(Theme.textPrimary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .background(Theme.card)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Theme.borderLight, lineWidth: 1)
                    )
            }
        }
    }
}

// MARK: - Screener Int Field

private struct ScreenerIntField: View {
    let label: String
    @Binding var value: Int
    let placeholder: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(Theme.textSecondary)

            TextField(placeholder, value: $value, format: .number)
                .keyboardType(.numberPad)
                .font(.system(size: 14))
                .foregroundColor(Theme.textPrimary)
                .padding(.horizontal, 10)
                .padding(.vertical, 8)
                .background(Theme.card)
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Theme.borderLight, lineWidth: 1)
                )
        }
    }
}

// MARK: - Screener Result Card

private struct ScreenerResultCard: View {
    let result: ScreenerResult

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Header: Strike + Type Badge
            HStack {
                Text(String(format: "%.0f", result.strikePrice))
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Text(result.optionType.shortName)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(
                        result.optionType == .call
                            ? Theme.accentGreen
                            : Theme.accentRed
                    )
                    .cornerRadius(6)

                Spacer()

                // LTP
                Text(String(format: "%.2f", result.ltp))
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(Theme.textPrimary)
            }

            // Grid of metrics
            HStack(spacing: 0) {
                ScreenerMetricItem(label: "IV", value: String(format: "%.1f%%", result.iv * 100))
                ScreenerMetricItem(label: "Delta", value: String(format: "%.3f", result.delta))
                ScreenerMetricItem(label: "OI", value: formatCompact(result.oi))
                ScreenerMetricItem(label: "Vol", value: formatCompact(result.volume))
            }

            // Secondary Greeks row
            HStack(spacing: 0) {
                ScreenerMetricItem(label: "Gamma", value: String(format: "%.5f", result.gamma))
                ScreenerMetricItem(label: "Theta", value: String(format: "%.2f", result.theta))
                ScreenerMetricItem(label: "Vega", value: String(format: "%.2f", result.vega))
                ScreenerMetricItem(
                    label: "OI Chg",
                    value: formatCompact(result.oiChange),
                    valueColor: result.oiChange >= 0 ? Theme.accentGreen : Theme.accentRed
                )
            }
        }
        .padding(14)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(Theme.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Theme.borderLight, lineWidth: 1)
                )
        )
    }

    private func formatCompact(_ value: Int) -> String {
        let absValue = abs(value)
        let sign = value < 0 ? "-" : ""
        if absValue >= 10_000_000 {
            return "\(sign)\(String(format: "%.1f", Double(absValue) / 1_000_000))M"
        } else if absValue >= 100_000 {
            return "\(sign)\(String(format: "%.1f", Double(absValue) / 100_000))L"
        } else if absValue >= 1_000 {
            return "\(sign)\(String(format: "%.1f", Double(absValue) / 1_000))K"
        }
        return "\(sign)\(absValue)"
    }
}

// MARK: - Screener Metric Item

private struct ScreenerMetricItem: View {
    let label: String
    let value: String
    var valueColor: Color = Theme.textPrimary

    var body: some View {
        VStack(spacing: 2) {
            Text(label)
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(Theme.textMuted)
            Text(value)
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(valueColor)
        }
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Screener FAB

struct ScreenerFAB: View {
    let action: () -> Void

    var body: some View {
        Button(action: {
            let generator = UIImpactFeedbackGenerator(style: .medium)
            generator.impactOccurred()
            action()
        }) {
            HStack(spacing: 8) {
                Image(systemName: "line.3.horizontal.decrease.circle.fill")
                    .font(.system(size: 16, weight: .semibold))
                Text("Screener")
                    .font(.system(size: 14, weight: .semibold))
            }
            .foregroundColor(Theme.textPrimary)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(
                LinearGradient(
                    colors: [Theme.accentOrange, Theme.accentRed],
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .cornerRadius(24)
            .shadow(color: Theme.accentOrange.opacity(0.4), radius: 8, x: 0, y: 4)
        }
        .buttonStyle(.plain)
    }
}
