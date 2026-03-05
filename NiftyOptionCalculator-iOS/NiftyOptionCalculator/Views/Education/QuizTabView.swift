import SwiftUI

// MARK: - Quiz Tab View

struct QuizTabView: View {
    @State private var selectedQuiz: Lesson? = nil
    @State private var showQuiz = false
    @State private var completedQuizzes: Set<String> = []
    @State private var quizScores: [String: Int] = [:]

    private let provider = EducationDataProvider.shared

    var quizzesAvailable: [Lesson] {
        provider.basicsLessons.filter { $0.quiz != nil }
    }

    var body: some View {
        ZStack {
            Theme.background.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 20) {
                    // Title
                    Text(L.educationQuiz)
                        .font(.system(size: 24, weight: .bold))
                        .foregroundColor(Theme.textPrimary)
                        .padding(.top, 20)

                    // Header Stats
                    quizStatsCard

                    // Quiz List
                    VStack(alignment: .leading, spacing: 12) {
                        Text(L.educationAvailableQuizzes)
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Theme.textMuted)
                            .padding(.horizontal, 4)

                        ForEach(quizzesAvailable) { lesson in
                            QuizCard(
                                lesson: lesson,
                                isCompleted: completedQuizzes.contains(lesson.id),
                                score: quizScores[lesson.id],
                                onTap: {
                                    selectedQuiz = lesson
                                    showQuiz = true
                                }
                            )
                        }
                    }

                    Spacer(minLength: 100)
                }
                .padding(16)
            }
        }
        .sheet(isPresented: $showQuiz) {
            if let lesson = selectedQuiz, let questions = lesson.quiz {
                QuizSheetView(
                    questions: questions,
                    lessonTitle: lesson.title,
                    onComplete: { score, total in
                        completedQuizzes.insert(lesson.id)
                        quizScores[lesson.id] = score
                    }
                )
            }
        }
    }

    // MARK: - Quiz Stats Card

    private var quizStatsCard: some View {
        HStack(spacing: 16) {
            // Completed
            VStack(spacing: 4) {
                Text("\(completedQuizzes.count)")
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundColor(Theme.profit)
                Text(L.educationCompleted)
                    .font(.system(size: 12))
                    .foregroundColor(Theme.textSecondary)
            }
            .frame(maxWidth: .infinity)

            Divider()
                .frame(height: 40)
                .background(Theme.border)

            // Available
            VStack(spacing: 4) {
                Text("\(quizzesAvailable.count)")
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundColor(Theme.accentBlue)
                Text(L.educationAvailable)
                    .font(.system(size: 12))
                    .foregroundColor(Theme.textSecondary)
            }
            .frame(maxWidth: .infinity)

            Divider()
                .frame(height: 40)
                .background(Theme.border)

            // Average Score
            VStack(spacing: 4) {
                Text(averageScoreText)
                    .font(.system(size: 28, weight: .bold, design: .rounded))
                    .foregroundColor(Theme.accentOrange)
                Text(L.educationAvgScore)
                    .font(.system(size: 12))
                    .foregroundColor(Theme.textSecondary)
            }
            .frame(maxWidth: .infinity)
        }
        .padding(16)
        .background(Theme.surface)
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    private var averageScoreText: String {
        guard !quizScores.isEmpty else { return "-" }
        let total = quizScores.values.reduce(0, +)
        let avg = Double(total) / Double(quizScores.count)
        return String(format: "%.0f%%", avg * 25) // Assuming 4 questions per quiz
    }
}

// MARK: - Quiz Card

struct QuizCard: View {
    let lesson: Lesson
    let isCompleted: Bool
    let score: Int?
    let onTap: () -> Void

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 14) {
                // Icon
                ZStack {
                    Circle()
                        .fill(isCompleted ? Theme.profit.opacity(0.15) : Theme.accentPurple.opacity(0.15))
                        .frame(width: 50, height: 50)

                    Image(systemName: isCompleted ? "checkmark.circle.fill" : "questionmark.circle.fill")
                        .font(.system(size: 24))
                        .foregroundColor(isCompleted ? Theme.profit : Theme.accentPurple)
                }

                // Content
                VStack(alignment: .leading, spacing: 4) {
                    Text(lesson.title)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(Theme.textPrimary)

                    HStack(spacing: 8) {
                        if let quiz = lesson.quiz {
                            Label("\(quiz.count) questions", systemImage: "list.bullet")
                                .font(.system(size: 12))
                                .foregroundColor(Theme.textSecondary)
                        }

                        if isCompleted, let score = score {
                            Text("•")
                                .foregroundColor(Theme.textMuted)
                            Label("\(score)/\(lesson.quiz?.count ?? 4) correct", systemImage: "star.fill")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(Theme.profit)
                        }
                    }
                }

                Spacer()

                // Action
                VStack {
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
                    .stroke(isCompleted ? Theme.profit.opacity(0.3) : Theme.border, lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Quiz Sheet View

struct QuizSheetView: View {
    let questions: [QuizQuestion]
    let lessonTitle: String
    let onComplete: (Int, Int) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var currentQuestionIndex = 0
    @State private var selectedAnswer: Int? = nil
    @State private var hasChecked = false
    @State private var score = 0
    @State private var showResults = false

    var currentQuestion: QuizQuestion {
        questions[currentQuestionIndex]
    }

    var isLastQuestion: Bool {
        currentQuestionIndex == questions.count - 1
    }

    var body: some View {
        NavigationView {
            ZStack {
                Theme.background.ignoresSafeArea()

                if showResults {
                    resultsView
                } else {
                    questionView
                }
            }
            .navigationTitle(lessonTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 24))
                            .foregroundColor(Theme.textMuted)
                    }
                }
            }
        }
    }

    // MARK: - Question View

    private var questionView: some View {
        VStack(spacing: 24) {
            // Progress
            VStack(spacing: 8) {
                HStack {
                    Text("\(L.educationQuestion) \(currentQuestionIndex + 1) of \(questions.count)")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(Theme.textSecondary)
                    Spacer()
                    Text("\(score) correct")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Theme.profit)
                }

                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        RoundedRectangle(cornerRadius: 4)
                            .fill(Theme.surface)
                            .frame(height: 8)

                        RoundedRectangle(cornerRadius: 4)
                            .fill(Theme.accentPurple)
                            .frame(width: geo.size.width * CGFloat(currentQuestionIndex + 1) / CGFloat(questions.count), height: 8)
                    }
                }
                .frame(height: 8)
            }
            .padding(.horizontal, 16)
            .padding(.top, 16)

            // Question
            Text(currentQuestion.question)
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(Theme.textPrimary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 20)

            // Options
            VStack(spacing: 12) {
                ForEach(Array(currentQuestion.options.enumerated()), id: \.offset) { index, option in
                    QuizOptionRow(
                        option: option,
                        index: index,
                        isSelected: selectedAnswer == index,
                        isCorrect: hasChecked ? index == currentQuestion.correctIndex : nil,
                        hasChecked: hasChecked
                    ) {
                        if !hasChecked {
                            selectedAnswer = index
                        }
                    }
                }
            }
            .padding(.horizontal, 16)

            Spacer()

            // Action Button
            Button {
                if hasChecked {
                    if isLastQuestion {
                        showResults = true
                        onComplete(score, questions.count)
                    } else {
                        currentQuestionIndex += 1
                        selectedAnswer = nil
                        hasChecked = false
                    }
                } else if selectedAnswer != nil {
                    hasChecked = true
                    if selectedAnswer == currentQuestion.correctIndex {
                        score += 1
                    }
                }
            } label: {
                Text(hasChecked ? (isLastQuestion ? L.educationFinish : L.educationNextQuestion) : L.educationCheckAnswer)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(selectedAnswer != nil ? Theme.accentPurple : Theme.textMuted)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            }
            .disabled(selectedAnswer == nil)
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
    }

    // MARK: - Results View

    private var resultsView: some View {
        VStack(spacing: 32) {
            Spacer()

            // Trophy
            Image(systemName: score == questions.count ? "trophy.fill" : "star.fill")
                .font(.system(size: 80))
                .foregroundColor(score == questions.count ? Theme.accentOrange : Theme.accentPurple)

            // Score
            VStack(spacing: 8) {
                Text(L.educationQuizComplete)
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(Theme.textPrimary)

                Text("\(score)/\(questions.count) correct")
                    .font(.system(size: 18))
                    .foregroundColor(Theme.textSecondary)

                // Percentage
                let percentage = Double(score) / Double(questions.count) * 100
                Text(String(format: "%.0f%%", percentage))
                    .font(.system(size: 48, weight: .bold, design: .rounded))
                    .foregroundColor(percentage >= 75 ? Theme.profit : (percentage >= 50 ? Theme.accentOrange : Theme.loss))
            }

            // Message
            Text(scoreMessage)
                .font(.system(size: 16))
                .foregroundColor(Theme.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Spacer()

            // Done Button
            Button {
                dismiss()
            } label: {
                Text(L.commonDone)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(Theme.accentPurple)
                    .clipShape(RoundedRectangle(cornerRadius: 14))
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 24)
        }
    }

    private var scoreMessage: String {
        let percentage = Double(score) / Double(questions.count) * 100
        if percentage == 100 {
            return "Perfect score! You're an options expert!"
        } else if percentage >= 75 {
            return "Great job! You have a solid understanding."
        } else if percentage >= 50 {
            return "Good effort! Review the lesson to improve."
        } else {
            return "Keep learning! Review the lesson and try again."
        }
    }
}

// MARK: - Quiz Option Row

struct QuizOptionRow: View {
    let option: String
    let index: Int
    let isSelected: Bool
    let isCorrect: Bool?
    let hasChecked: Bool
    let onTap: () -> Void

    var backgroundColor: Color {
        if hasChecked {
            if let isCorrect = isCorrect, isCorrect {
                return Theme.profit.opacity(0.15)
            } else if isSelected {
                return Theme.loss.opacity(0.15)
            }
        }
        return isSelected ? Theme.accentPurple.opacity(0.15) : Theme.surface
    }

    var borderColor: Color {
        if hasChecked {
            if let isCorrect = isCorrect, isCorrect {
                return Theme.profit
            } else if isSelected {
                return Theme.loss
            }
        }
        return isSelected ? Theme.accentPurple : Theme.border
    }

    var body: some View {
        Button(action: onTap) {
            HStack(spacing: 12) {
                // Letter
                Text(["A", "B", "C", "D"][index])
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(isSelected ? .white : Theme.textSecondary)
                    .frame(width: 32, height: 32)
                    .background(isSelected ? Theme.accentPurple : Theme.background)
                    .clipShape(Circle())

                // Option text
                Text(option)
                    .font(.system(size: 15))
                    .foregroundColor(Theme.textPrimary)
                    .multilineTextAlignment(.leading)

                Spacer()

                // Result icon
                if hasChecked {
                    if let isCorrect = isCorrect, isCorrect {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(Theme.profit)
                    } else if isSelected {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(Theme.loss)
                    }
                }
            }
            .padding(14)
            .background(backgroundColor)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(borderColor, lineWidth: isSelected ? 2 : 1)
            )
        }
        .buttonStyle(.plain)
        .disabled(hasChecked)
    }
}

// MARK: - Preview

#Preview {
    NavigationStack {
        QuizTabView()
    }
}
