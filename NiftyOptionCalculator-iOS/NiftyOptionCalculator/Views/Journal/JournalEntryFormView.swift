import SwiftUI

// MARK: - Journal Entry Form View

struct JournalEntryFormView: View {
    @ObservedObject var viewModel: TradeJournalViewModel
    @Environment(\.dismiss) private var dismiss

    var isEditing: Bool { viewModel.editingEntry != nil }

    var body: some View {
        NavigationView {
            ZStack {
                Theme.backgroundGradient.ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 20) {
                        // Trade Details Section
                        tradeDetailsSection

                        // Pricing Section
                        pricingSection

                        // Dates Section
                        datesSection

                        // Tags Section
                        tagsSection

                        // Mood Section
                        moodSection

                        // Market Condition Section
                        marketConditionSection

                        // Outcome Section
                        outcomeSection

                        // Notes Section
                        notesSection
                    }
                    .padding()
                }
            }
            .navigationTitle(isEditing ? "Edit Entry" : "New Journal Entry")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") {
                        viewModel.resetForm()
                        dismiss()
                    }
                    .foregroundColor(Theme.textSecondary)
                }

                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        Task {
                            if isEditing {
                                await viewModel.updateEntry()
                            } else {
                                await viewModel.createEntry()
                            }
                        }
                    } label: {
                        Text(isEditing ? "Update" : "Save")
                            .font(.headline)
                            .foregroundColor(Theme.primaryBlue)
                    }
                    .disabled(viewModel.isLoading)
                }
            }
        }
    }

    // MARK: - Trade Details

    private var tradeDetailsSection: some View {
        formSection(title: "Trade Details") {
            // Symbol picker
            VStack(alignment: .leading, spacing: 6) {
                Text("Symbol")
                    .font(.caption)
                    .foregroundColor(Theme.textSecondary)

                if isEditing {
                    Text(viewModel.formSymbol)
                        .font(.subheadline)
                        .foregroundColor(Theme.textPrimary)
                        .padding(.vertical, 8)
                } else {
                    Picker("Symbol", selection: $viewModel.formSymbol) {
                        ForEach(viewModel.availableSymbols, id: \.self) { symbol in
                            Text(symbol).tag(symbol)
                        }
                    }
                    .pickerStyle(.segmented)
                }
            }

            // Strike price
            formTextField(label: "Strike Price", text: $viewModel.formStrikePrice, placeholder: "e.g. 24000", keyboardType: .decimalPad, disabled: isEditing)

            // Option type
            VStack(alignment: .leading, spacing: 6) {
                Text("Option Type")
                    .font(.caption)
                    .foregroundColor(Theme.textSecondary)

                if isEditing {
                    Text(viewModel.formOptionType)
                        .font(.subheadline)
                        .foregroundColor(Theme.textPrimary)
                        .padding(.vertical, 4)
                } else {
                    Picker("Option Type", selection: $viewModel.formOptionType) {
                        Text("CE (Call)").tag("CE")
                        Text("PE (Put)").tag("PE")
                    }
                    .pickerStyle(.segmented)
                }
            }

            // Direction
            VStack(alignment: .leading, spacing: 6) {
                Text("Direction")
                    .font(.caption)
                    .foregroundColor(Theme.textSecondary)

                if isEditing {
                    Text(viewModel.formDirection.uppercased())
                        .font(.subheadline)
                        .foregroundColor(viewModel.formDirection == "buy" ? Theme.profit : Theme.loss)
                        .padding(.vertical, 4)
                } else {
                    HStack(spacing: 12) {
                        directionButton(title: "BUY", value: "buy", color: Theme.profit)
                        directionButton(title: "SELL", value: "sell", color: Theme.loss)
                    }
                }
            }
        }
    }

    private func directionButton(title: String, value: String, color: Color) -> some View {
        Button {
            viewModel.formDirection = value
        } label: {
            Text(title)
                .font(.subheadline.bold())
                .foregroundColor(viewModel.formDirection == value ? .white : color)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 10)
                .background(
                    viewModel.formDirection == value ?
                    AnyShapeStyle(color) :
                    AnyShapeStyle(color.opacity(0.15))
                )
                .cornerRadius(10)
        }
    }

    // MARK: - Pricing Section

    private var pricingSection: some View {
        formSection(title: "Pricing") {
            formTextField(label: "Entry Price", text: $viewModel.formEntryPrice, placeholder: "e.g. 150.50", keyboardType: .decimalPad, disabled: isEditing)
            formTextField(label: "Exit Price (optional)", text: $viewModel.formExitPrice, placeholder: "e.g. 200.00", keyboardType: .decimalPad)

            HStack(spacing: 12) {
                formTextField(label: "Quantity (lots)", text: $viewModel.formQuantity, placeholder: "1", keyboardType: .numberPad, disabled: isEditing)
                formTextField(label: "Lot Size", text: $viewModel.formLotSize, placeholder: "75", keyboardType: .numberPad, disabled: isEditing)
            }
        }
    }

    // MARK: - Dates Section

    private var datesSection: some View {
        formSection(title: "Dates") {
            VStack(alignment: .leading, spacing: 6) {
                Text("Entry Date")
                    .font(.caption)
                    .foregroundColor(Theme.textSecondary)
                DatePicker("", selection: $viewModel.formEntryDate, displayedComponents: .date)
                    .datePickerStyle(.compact)
                    .labelsHidden()
                    .disabled(isEditing)
            }

            Toggle(isOn: $viewModel.formHasExitDate) {
                Text("Exit Date")
                    .font(.caption)
                    .foregroundColor(Theme.textSecondary)
            }
            .tint(Theme.primaryBlue)

            if viewModel.formHasExitDate {
                DatePicker("", selection: $viewModel.formExitDate, displayedComponents: .date)
                    .datePickerStyle(.compact)
                    .labelsHidden()
            }

            Toggle(isOn: $viewModel.formHasExpiryDate) {
                Text("Expiry Date")
                    .font(.caption)
                    .foregroundColor(Theme.textSecondary)
            }
            .tint(Theme.primaryBlue)

            if viewModel.formHasExpiryDate {
                DatePicker("", selection: $viewModel.formExpiryDate, displayedComponents: .date)
                    .datePickerStyle(.compact)
                    .labelsHidden()
            }
        }
    }

    // MARK: - Tags Section

    private var tagsSection: some View {
        formSection(title: "Tags") {
            tagFlowLayout
        }
    }

    private var tagFlowLayout: some View {
        let columns = [
            GridItem(.adaptive(minimum: 90), spacing: 8)
        ]

        return LazyVGrid(columns: columns, spacing: 8) {
            ForEach(JournalTag.allCases, id: \.self) { tag in
                Button {
                    if viewModel.formSelectedTags.contains(tag) {
                        viewModel.formSelectedTags.remove(tag)
                    } else {
                        viewModel.formSelectedTags.insert(tag)
                    }
                } label: {
                    Text(tag.displayName)
                        .font(.caption.bold())
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .frame(maxWidth: .infinity)
                        .background(
                            viewModel.formSelectedTags.contains(tag) ?
                            Theme.primaryBlue.opacity(0.2) :
                            Theme.surface
                        )
                        .foregroundColor(
                            viewModel.formSelectedTags.contains(tag) ?
                            Theme.primaryBlue :
                            Theme.textSecondary
                        )
                        .cornerRadius(8)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(
                                    viewModel.formSelectedTags.contains(tag) ?
                                    Theme.primaryBlue.opacity(0.5) :
                                    Theme.border,
                                    lineWidth: 1
                                )
                        )
                }
            }
        }
    }

    // MARK: - Mood Section

    private var moodSection: some View {
        formSection(title: "Mood") {
            let columns = [
                GridItem(.adaptive(minimum: 90), spacing: 8)
            ]

            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(TradeMood.allCases, id: \.self) { mood in
                    Button {
                        if viewModel.formMood == mood {
                            viewModel.formMood = nil
                        } else {
                            viewModel.formMood = mood
                        }
                    } label: {
                        VStack(spacing: 4) {
                            Text(mood.emoji)
                                .font(.title3)
                            Text(mood.rawValue.capitalized)
                                .font(.system(size: 10, weight: .medium))
                                .foregroundColor(
                                    viewModel.formMood == mood ?
                                    Theme.primaryBlue :
                                    Theme.textSecondary
                                )
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .background(
                            viewModel.formMood == mood ?
                            Theme.primaryBlue.opacity(0.15) :
                            Theme.surface
                        )
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(
                                    viewModel.formMood == mood ?
                                    Theme.primaryBlue.opacity(0.5) :
                                    Theme.border,
                                    lineWidth: 1
                                )
                        )
                    }
                }
            }
        }
    }

    // MARK: - Market Condition Section

    private var marketConditionSection: some View {
        formSection(title: "Market Condition") {
            let columns = [
                GridItem(.adaptive(minimum: 100), spacing: 8)
            ]

            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(MarketCondition.allCases, id: \.self) { condition in
                    Button {
                        if viewModel.formMarketCondition == condition {
                            viewModel.formMarketCondition = nil
                        } else {
                            viewModel.formMarketCondition = condition
                        }
                    } label: {
                        Text(condition.displayName)
                            .font(.caption.bold())
                            .padding(.horizontal, 10)
                            .padding(.vertical, 8)
                            .frame(maxWidth: .infinity)
                            .background(
                                viewModel.formMarketCondition == condition ?
                                Theme.accentPurple.opacity(0.2) :
                                Theme.surface
                            )
                            .foregroundColor(
                                viewModel.formMarketCondition == condition ?
                                Theme.accentPurple :
                                Theme.textSecondary
                            )
                            .cornerRadius(8)
                            .overlay(
                                RoundedRectangle(cornerRadius: 8)
                                    .stroke(
                                        viewModel.formMarketCondition == condition ?
                                        Theme.accentPurple.opacity(0.5) :
                                        Theme.border,
                                        lineWidth: 1
                                    )
                            )
                    }
                }
            }
        }
    }

    // MARK: - Outcome Section

    private var outcomeSection: some View {
        formSection(title: "Outcome") {
            HStack(spacing: 10) {
                ForEach(TradeOutcome.allCases, id: \.self) { outcome in
                    Button {
                        if viewModel.formOutcome == outcome {
                            viewModel.formOutcome = nil
                        } else {
                            viewModel.formOutcome = outcome
                        }
                    } label: {
                        Text(outcome.rawValue.capitalized)
                            .font(.subheadline.bold())
                            .foregroundColor(
                                viewModel.formOutcome == outcome ?
                                .white :
                                outcomeColor(outcome)
                            )
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(
                                viewModel.formOutcome == outcome ?
                                AnyShapeStyle(outcomeColor(outcome)) :
                                AnyShapeStyle(outcomeColor(outcome).opacity(0.15))
                            )
                            .cornerRadius(10)
                    }
                }
            }
        }
    }

    private func outcomeColor(_ outcome: TradeOutcome) -> Color {
        switch outcome {
        case .profit: return Theme.profit
        case .loss: return Theme.loss
        case .breakeven: return Theme.textMuted
        }
    }

    // MARK: - Notes Section

    private var notesSection: some View {
        formSection(title: "Notes") {
            VStack(alignment: .leading, spacing: 6) {
                Text("Title (optional)")
                    .font(.caption)
                    .foregroundColor(Theme.textSecondary)

                TextField("e.g. Breakout trade on NIFTY", text: $viewModel.formTitle)
                    .font(.subheadline)
                    .padding(10)
                    .background(Theme.surface)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Theme.border, lineWidth: 1)
                    )
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("Notes (optional)")
                    .font(.caption)
                    .foregroundColor(Theme.textSecondary)

                TextEditor(text: $viewModel.formNotes)
                    .font(.subheadline)
                    .frame(minHeight: 80)
                    .padding(6)
                    .background(Theme.surface)
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(Theme.border, lineWidth: 1)
                    )
            }
        }
    }

    // MARK: - Helpers

    private func formSection<Content: View>(title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.headline)
                .foregroundColor(Theme.textPrimary)

            content()
        }
        .padding()
        .background(Theme.card)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Theme.border, lineWidth: 1)
        )
    }

    private func formTextField(label: String, text: Binding<String>, placeholder: String, keyboardType: UIKeyboardType = .default, disabled: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(label)
                .font(.caption)
                .foregroundColor(Theme.textSecondary)

            TextField(placeholder, text: text)
                .font(.subheadline)
                .keyboardType(keyboardType)
                .padding(10)
                .background(disabled ? Theme.surface.opacity(0.5) : Theme.surface)
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Theme.border, lineWidth: 1)
                )
                .disabled(disabled)
                .foregroundColor(disabled ? Theme.textMuted : Theme.textPrimary)
        }
    }
}
