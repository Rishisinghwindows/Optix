package com.optix.app.presentation.screens.education

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optix.app.domain.model.QuizQuestion
import com.optix.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    questions: List<QuizQuestion>,
    currentIndex: Int,
    answers: Map<Int, Int>,
    showResults: Boolean,
    score: Int,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    val currentQuestion = if (currentIndex < questions.size) questions[currentIndex] else null
    val selectedAnswer = answers[currentIndex]
    var showExplanation by remember(currentIndex) { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quiz", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        if (showResults) {
            QuizResultsScreen(
                score = score,
                totalQuestions = questions.size,
                onRetry = onRetry,
                onFinish = onFinish,
                modifier = Modifier.padding(padding)
            )
        } else if (currentQuestion != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Progress Bar
                QuizProgressBar(
                    currentIndex = currentIndex,
                    totalQuestions = questions.size,
                    score = score
                )

                // Question Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Question Number
                    Text(
                        text = "Question ${currentIndex + 1} of ${questions.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondaryDark
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Question Text
                    Text(
                        text = currentQuestion.question,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 32.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Options
                    currentQuestion.options.forEachIndexed { index, option ->
                        QuizOptionItem(
                            text = option,
                            index = index,
                            isSelected = selectedAnswer == index,
                            isCorrect = if (showExplanation) index == currentQuestion.correctIndex else null,
                            isWrong = showExplanation && selectedAnswer == index && index != currentQuestion.correctIndex,
                            enabled = !showExplanation,
                            onClick = { onAnswer(index) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Explanation
                    AnimatedVisibility(
                        visible = showExplanation,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        ExplanationCard(
                            isCorrect = selectedAnswer == currentQuestion.correctIndex,
                            explanation = currentQuestion.explanation
                        )
                    }
                }

                // Bottom Action Button
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp
                ) {
                    Button(
                        onClick = {
                            if (showExplanation) {
                                showExplanation = false
                                onNext()
                            } else if (selectedAnswer != null) {
                                showExplanation = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        enabled = selectedAnswer != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedAnswer != null) AccentPurple else TextSecondaryDark
                        )
                    ) {
                        Text(
                            text = when {
                                showExplanation && currentIndex < questions.size - 1 -> "Next Question"
                                showExplanation -> "See Results"
                                else -> "Check Answer"
                            },
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizProgressBar(
    currentIndex: Int,
    totalQuestions: Int,
    score: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Progress",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondaryDark
            )
            Text(
                text = "$score correct",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryGreen
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(totalQuestions) { index ->
                val progress by animateFloatAsState(
                    targetValue = if (index <= currentIndex) 1f else 0f,
                    animationSpec = tween(300),
                    label = "progress"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (index <= currentIndex) AccentPurple
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }
}

@Composable
private fun QuizOptionItem(
    text: String,
    index: Int,
    isSelected: Boolean,
    isCorrect: Boolean?,
    isWrong: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val optionLabels = listOf("A", "B", "C", "D")

    val backgroundColor = when {
        isCorrect == true -> PrimaryGreen.copy(alpha = 0.15f)
        isWrong -> ErrorRed.copy(alpha = 0.15f)
        isSelected -> AccentPurple.copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surface
    }

    val borderColor = when {
        isCorrect == true -> PrimaryGreen
        isWrong -> ErrorRed
        isSelected -> AccentPurple
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    val labelBackgroundColor = when {
        isSelected -> AccentPurple
        else -> MaterialTheme.colorScheme.background
    }

    val labelTextColor = when {
        isSelected -> Color.White
        else -> TextSecondaryDark
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = BorderStroke(
            width = if (isSelected || isCorrect == true || isWrong) 2.dp else 1.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Option Label (A, B, C, D)
            Surface(
                shape = CircleShape,
                color = labelBackgroundColor,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = optionLabels.getOrElse(index) { "" },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = labelTextColor
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Option Text
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )

            // Result Icon
            if (isCorrect == true) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(24.dp)
                )
            } else if (isWrong) {
                Icon(
                    Icons.Default.Cancel,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ExplanationCard(
    isCorrect: Boolean,
    explanation: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCorrect)
                PrimaryGreen.copy(alpha = 0.1f)
            else
                ErrorRed.copy(alpha = 0.1f)
        ),
        border = BorderStroke(
            1.dp,
            if (isCorrect) PrimaryGreen.copy(alpha = 0.3f) else ErrorRed.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (isCorrect) PrimaryGreen else ErrorRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCorrect) "Correct!" else "Incorrect",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isCorrect) PrimaryGreen else ErrorRed
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryDark
            )
        }
    }
}

@Composable
private fun QuizResultsScreen(
    score: Int,
    totalQuestions: Int,
    onRetry: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val percentage = (score.toFloat() / totalQuestions) * 100
    val isPerfect = score == totalQuestions
    val isGood = percentage >= 75
    val isPassing = percentage >= 50

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Trophy/Star Icon
        Surface(
            shape = CircleShape,
            color = when {
                isPerfect -> AccentOrange.copy(alpha = 0.15f)
                isGood -> PrimaryGreen.copy(alpha = 0.15f)
                isPassing -> WarningOrange.copy(alpha = 0.15f)
                else -> ErrorRed.copy(alpha = 0.15f)
            },
            modifier = Modifier.size(100.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (isPerfect) Icons.Default.EmojiEvents else Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(50.dp),
                    tint = when {
                        isPerfect -> AccentOrange
                        isGood -> PrimaryGreen
                        isPassing -> WarningOrange
                        else -> ErrorRed
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Title
        Text(
            text = "Quiz Complete!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Score
        Text(
            text = "$score/$totalQuestions correct",
            style = MaterialTheme.typography.titleLarge,
            color = TextSecondaryDark
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Percentage
        Text(
            text = "${percentage.toInt()}%",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = when {
                isPerfect || isGood -> PrimaryGreen
                isPassing -> WarningOrange
                else -> ErrorRed
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Message
        Text(
            text = when {
                isPerfect -> "Perfect score! You're an options expert!"
                isGood -> "Great job! You have a solid understanding."
                isPassing -> "Good effort! Review the lesson to improve."
                else -> "Keep learning! Review the lesson and try again."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondaryDark,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AccentPurple
                ),
                border = BorderStroke(1.dp, AccentPurple)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Try Again", fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onFinish,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
            ) {
                Icon(
                    Icons.Default.Done,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}
