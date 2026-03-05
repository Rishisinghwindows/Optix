import SwiftUI

// MARK: - Lesson Detail View

struct LessonDetailView: View {
    let lesson: Lesson

    @State private var showQuiz = false
    @State private var quizCompleted = false
    @State private var quizScore = 0

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                // Header
                lessonHeader

                // Key Points
                keyPointsSection

                // Content Sections
                ForEach(lesson.content) { section in
                    LessonSectionView(section: section)
                }

                // Quiz Button
                if lesson.quiz != nil {
                    quizSection
                }

                Spacer(minLength: 40)
            }
            .padding(16)
        }
        .background(Theme.background.ignoresSafeArea())
        .navigationTitle(lesson.title)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showQuiz) {
            if let quiz = lesson.quiz {
                QuizView(
                    questions: quiz,
                    onComplete: { score in
                        quizScore = score
                        quizCompleted = true
                        showQuiz = false
                    }
                )
            }
        }
    }

    // MARK: - Header

    private var lessonHeader: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: lesson.icon)
                    .font(.system(size: 32, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(width: 60, height: 60)
                    .background(
                        LinearGradient(
                            colors: [Theme.accentBlue, Theme.accentPurple],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .clipShape(RoundedRectangle(cornerRadius: 14))

                VStack(alignment: .leading, spacing: 4) {
                    Text(lesson.subtitle)
                        .font(.system(size: 15))
                        .foregroundColor(Theme.textSecondary)

                    HStack(spacing: 12) {
                        Label(lesson.duration, systemImage: "clock")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(Theme.textMuted)

                        if lesson.quiz != nil {
                            Label(L.educationQuizIncluded, systemImage: "checkmark.circle.fill")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(Theme.accentGreen)
                        }
                    }
                }

                Spacer()
            }
        }
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    // MARK: - Key Points Section

    private var keyPointsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(L.educationKeyPoints)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(Theme.textPrimary)

            VStack(alignment: .leading, spacing: 8) {
                ForEach(lesson.keyPoints, id: \.self) { point in
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 14))
                            .foregroundColor(Theme.accentGreen)
                            .padding(.top, 2)

                        Text(point)
                            .font(.system(size: 14))
                            .foregroundColor(Theme.textSecondary)
                    }
                }
            }
        }
        .padding(16)
        .background(Theme.accentGreen.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Theme.accentGreen.opacity(0.3), lineWidth: 1)
        )
    }

    // MARK: - Quiz Section

    private var quizSection: some View {
        VStack(spacing: 12) {
            if quizCompleted {
                // Score Display
                VStack(spacing: 8) {
                    Image(systemName: quizScore == lesson.quiz?.count ? "star.fill" : "checkmark.circle.fill")
                        .font(.system(size: 40))
                        .foregroundColor(quizScore == lesson.quiz?.count ? Theme.accentYellow : Theme.accentGreen)

                    Text(L.educationQuizComplete)
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(Theme.textPrimary)

                    Text("\(L.educationScore): \(quizScore)/\(lesson.quiz?.count ?? 0)")
                        .font(.system(size: 15))
                        .foregroundColor(Theme.textSecondary)
                }
                .padding(.vertical, 20)
            }

            Button {
                showQuiz = true
            } label: {
                HStack {
                    Image(systemName: quizCompleted ? "arrow.counterclockwise" : "play.fill")
                        .font(.system(size: 16, weight: .semibold))
                    Text(quizCompleted ? L.educationRetakeQuiz : L.educationTakeQuiz)
                        .font(.system(size: 16, weight: .semibold))
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(Theme.accentBlue)
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
        }
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

// MARK: - Lesson Section View

struct LessonSectionView: View {
    let section: LessonSection

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Section Title
            if let title = section.title {
                Text(title)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(Theme.textPrimary)
            }

            // Content
            Text(section.content)
                .font(.system(size: 15))
                .foregroundColor(Theme.textSecondary)
                .lineSpacing(4)

            // Example
            if let example = section.example {
                ExampleCard(example: example)
            }

            // Tip
            if let tip = section.tip {
                TipView(tip: tip)
            }
        }
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

// MARK: - Example Card

struct ExampleCard: View {
    let example: LessonExample

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Image(systemName: "lightbulb.fill")
                    .font(.system(size: 14))
                    .foregroundColor(Theme.accentYellow)
                Text(example.title)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(Theme.accentYellow)
            }

            Text(example.scenario)
                .font(.system(size: 14))
                .foregroundColor(Theme.textSecondary)

            if let calculation = example.calculation {
                Text(calculation)
                    .font(.system(size: 13, design: .monospaced))
                    .foregroundColor(Theme.textPrimary)
                    .padding(10)
                    .background(Theme.background)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
            }

            Text(example.result)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(Theme.profit)
        }
        .padding(14)
        .background(Theme.accentYellow.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Theme.accentYellow.opacity(0.3), lineWidth: 1)
        )
    }
}

// MARK: - Tip View

struct TipView: View {
    let tip: String

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "info.circle.fill")
                .font(.system(size: 16))
                .foregroundColor(Theme.accentBlue)

            Text(tip)
                .font(.system(size: 13))
                .foregroundColor(Theme.textSecondary)
                .italic()
        }
        .padding(12)
        .background(Theme.accentBlue.opacity(0.1))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

// MARK: - Quiz View

struct QuizView: View {
    let questions: [QuizQuestion]
    let onComplete: (Int) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var currentIndex = 0
    @State private var selectedAnswer: Int?
    @State private var showExplanation = false
    @State private var correctAnswers = 0

    private var currentQuestion: QuizQuestion {
        questions[currentIndex]
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Theme.background.ignoresSafeArea()

                VStack(spacing: 20) {
                    // Progress
                    HStack(spacing: 4) {
                        ForEach(0..<questions.count, id: \.self) { index in
                            RoundedRectangle(cornerRadius: 2)
                                .fill(index <= currentIndex ? Theme.accentBlue : Theme.border)
                                .frame(height: 4)
                        }
                    }
                    .padding(.horizontal, 16)

                    ScrollView {
                        VStack(alignment: .leading, spacing: 20) {
                            // Question Number
                            Text("\(L.educationQuestion) \(currentIndex + 1)/\(questions.count)")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(Theme.textMuted)

                            // Question
                            Text(currentQuestion.question)
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(Theme.textPrimary)

                            // Options
                            VStack(spacing: 12) {
                                ForEach(0..<currentQuestion.options.count, id: \.self) { index in
                                    QuizOptionButton(
                                        text: currentQuestion.options[index],
                                        isSelected: selectedAnswer == index,
                                        isCorrect: showExplanation ? index == currentQuestion.correctIndex : nil,
                                        isWrong: showExplanation && selectedAnswer == index && index != currentQuestion.correctIndex
                                    ) {
                                        if !showExplanation {
                                            selectedAnswer = index
                                        }
                                    }
                                }
                            }

                            // Explanation
                            if showExplanation {
                                VStack(alignment: .leading, spacing: 8) {
                                    HStack {
                                        Image(systemName: selectedAnswer == currentQuestion.correctIndex ? "checkmark.circle.fill" : "xmark.circle.fill")
                                            .foregroundColor(selectedAnswer == currentQuestion.correctIndex ? Theme.profit : Theme.loss)
                                        Text(selectedAnswer == currentQuestion.correctIndex ? L.educationCorrect : L.educationIncorrect)
                                            .font(.system(size: 16, weight: .bold))
                                            .foregroundColor(selectedAnswer == currentQuestion.correctIndex ? Theme.profit : Theme.loss)
                                    }

                                    Text(currentQuestion.explanation)
                                        .font(.system(size: 14))
                                        .foregroundColor(Theme.textSecondary)
                                }
                                .padding(14)
                                .background(Theme.surface)
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                            }
                        }
                        .padding(16)
                    }

                    // Action Button
                    Button {
                        if showExplanation {
                            nextQuestion()
                        } else if selectedAnswer != nil {
                            checkAnswer()
                        }
                    } label: {
                        Text(buttonTitle)
                            .font(.system(size: 17, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .background(selectedAnswer != nil ? Theme.accentBlue : Theme.textMuted)
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                    .disabled(selectedAnswer == nil)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                }
            }
            .navigationTitle(L.educationQuiz)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(L.educationClose) {
                        dismiss()
                    }
                    .foregroundColor(Theme.accentBlue)
                }
            }
        }
    }

    private var buttonTitle: String {
        if showExplanation {
            return currentIndex < questions.count - 1 ? L.educationNextQuestion : L.educationFinish
        }
        return L.educationCheckAnswer
    }

    private func checkAnswer() {
        showExplanation = true
        if selectedAnswer == currentQuestion.correctIndex {
            correctAnswers += 1
        }
    }

    private func nextQuestion() {
        if currentIndex < questions.count - 1 {
            currentIndex += 1
            selectedAnswer = nil
            showExplanation = false
        } else {
            onComplete(correctAnswers)
        }
    }
}

// MARK: - Quiz Option Button

struct QuizOptionButton: View {
    let text: String
    let isSelected: Bool
    let isCorrect: Bool?
    let isWrong: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack {
                Text(text)
                    .font(.system(size: 15))
                    .foregroundColor(Theme.textPrimary)
                    .multilineTextAlignment(.leading)

                Spacer()

                if let isCorrect = isCorrect {
                    Image(systemName: isCorrect ? "checkmark.circle.fill" : (isWrong ? "xmark.circle.fill" : "circle"))
                        .foregroundColor(isCorrect ? Theme.profit : (isWrong ? Theme.loss : Theme.textMuted))
                } else if isSelected {
                    Image(systemName: "circle.fill")
                        .foregroundColor(Theme.accentBlue)
                } else {
                    Image(systemName: "circle")
                        .foregroundColor(Theme.textMuted)
                }
            }
            .padding(14)
            .background(backgroundColor)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(borderColor, lineWidth: 2)
            )
        }
    }

    private var backgroundColor: Color {
        if isCorrect == true {
            return Theme.profit.opacity(0.1)
        } else if isWrong {
            return Theme.loss.opacity(0.1)
        } else if isSelected {
            return Theme.accentBlue.opacity(0.1)
        }
        return Theme.surface
    }

    private var borderColor: Color {
        if isCorrect == true {
            return Theme.profit
        } else if isWrong {
            return Theme.loss
        } else if isSelected {
            return Theme.accentBlue
        }
        return Theme.border
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        LessonDetailView(lesson: EducationDataProvider.shared.basicsLessons[0])
    }
}
