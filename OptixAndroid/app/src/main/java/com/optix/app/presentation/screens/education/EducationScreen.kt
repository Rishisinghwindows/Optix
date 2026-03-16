package com.optix.app.presentation.screens.education

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.optix.app.domain.model.*
import com.optix.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EducationScreen(
    viewModel: EducationViewModel = hiltViewModel(),
    onBack: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()

    // Handle navigation within education
    when {
        state.isQuizActive -> {
            QuizScreen(
                questions = state.currentQuizQuestions,
                currentIndex = state.currentQuizIndex,
                answers = state.quizAnswers,
                showResults = state.showQuizResults,
                score = state.quizScore,
                onAnswer = { viewModel.answerQuestion(it) },
                onNext = { viewModel.nextQuestion() },
                onFinish = { viewModel.finishQuiz() },
                onRetry = { viewModel.resetQuiz() },
                onBack = { viewModel.finishQuiz() }
            )
        }
        state.selectedLesson != null -> {
            LessonScreen(
                lesson = state.selectedLesson!!,
                onBack = { viewModel.selectLesson(null) },
                onStartQuiz = { questions ->
                    viewModel.startQuiz(questions)
                },
                onComplete = {
                    viewModel.markLessonCompleted(state.selectedLesson!!.id)
                    viewModel.selectLesson(null)
                }
            )
        }
        state.selectedGreek != null -> {
            GreeksEducationScreen(
                greek = state.selectedGreek!!,
                onBack = { viewModel.selectGreek(null) }
            )
        }
        state.selectedStrategy != null -> {
            StrategyDetailScreen(
                strategy = state.selectedStrategy!!,
                onBack = { viewModel.selectStrategy(null) }
            )
        }
        else -> {
            // Main Education Hub
            EducationHubScreen(
                state = state,
                onBack = onBack,
                onCategorySelect = { viewModel.selectCategory(it) },
                onLessonSelect = { viewModel.selectLesson(it) },
                onGreekSelect = { viewModel.selectGreek(it) },
                onStrategySelect = { viewModel.selectStrategy(it) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EducationHubScreen(
    state: EducationUiState,
    onBack: () -> Unit,
    onCategorySelect: (EducationCategory) -> Unit,
    onLessonSelect: (Lesson) -> Unit,
    onGreekSelect: (GreekInfo) -> Unit,
    onStrategySelect: (OptionStrategy) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Learn Options", fontWeight = FontWeight.Bold)
                        Text(
                            "${state.completedLessonsCount}/${state.totalLessonsCount} lessons completed",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryDark
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Progress Card
            ProgressCard(
                completed = state.completedLessonsCount,
                total = state.totalLessonsCount
            )

            // Category Selector
            CategorySelector(
                selectedCategory = state.selectedCategory,
                onCategorySelect = onCategorySelect
            )

            // Content based on category
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (state.selectedCategory) {
                    EducationCategory.BASICS -> {
                        items(state.filteredLessons) { lesson ->
                            LessonCard(
                                lesson = lesson,
                                isCompleted = state.progress.completedLessons.contains(lesson.id),
                                onClick = { onLessonSelect(lesson) }
                            )
                        }
                    }
                    EducationCategory.GREEKS -> {
                        items(state.greeks) { greek ->
                            GreekCard(
                                greek = greek,
                                onClick = { onGreekSelect(greek) }
                            )
                        }
                    }
                    EducationCategory.STRATEGIES -> {
                        items(state.strategies) { strategy ->
                            StrategyCard(
                                strategy = strategy,
                                onClick = { onStrategySelect(strategy) }
                            )
                        }
                    }
                    EducationCategory.ADVANCED -> {
                        items(state.lessons.filter { it.category == EducationCategory.ADVANCED }) { lesson ->
                            LessonCard(
                                lesson = lesson,
                                isCompleted = state.progress.completedLessons.contains(lesson.id),
                                onClick = { onLessonSelect(lesson) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressCard(completed: Int, total: Int) {
    val progress = if (total > 0) completed.toFloat() / total else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = PrimaryGreen.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Your Progress",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$completed of $total lessons completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = PrimaryGreen
                ) {
                    Text(
                        "${(progress * 100).toInt()}%",
                        modifier = Modifier.padding(12.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = PrimaryGreen,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun CategorySelector(
    selectedCategory: EducationCategory,
    onCategorySelect: (EducationCategory) -> Unit
) {
    val categories = listOf(
        Triple(EducationCategory.BASICS, "Basics", Icons.Default.MenuBook),
        Triple(EducationCategory.GREEKS, "Greeks", Icons.Default.Functions),
        Triple(EducationCategory.STRATEGIES, "Strategies", Icons.Default.TrendingUp),
        Triple(EducationCategory.ADVANCED, "Advanced", Icons.Default.School)
    )

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { (category, label, icon) ->
            val isSelected = selectedCategory == category
            val color = when (category) {
                EducationCategory.BASICS -> AccentBlue
                EducationCategory.GREEKS -> AccentPurple
                EducationCategory.STRATEGIES -> PrimaryGreen
                EducationCategory.ADVANCED -> WarningOrange
            }

            FilterChip(
                selected = isSelected,
                onClick = { onCategorySelect(category) },
                label = { Text(label) },
                leadingIcon = {
                    Icon(icon, null, Modifier.size(16.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color.copy(alpha = 0.15f),
                    selectedLabelColor = color,
                    selectedLeadingIconColor = color
                )
            )
        }
    }
}

@Composable
fun LessonCard(
    lesson: Lesson,
    isCompleted: Boolean = false,
    onClick: () -> Unit
) {
    val categoryColor = when (lesson.category) {
        EducationCategory.BASICS -> AccentBlue
        EducationCategory.GREEKS -> AccentPurple
        EducationCategory.STRATEGIES -> PrimaryGreen
        EducationCategory.ADVANCED -> WarningOrange
    }

    val difficultyColor = when (lesson.difficulty) {
        LessonDifficulty.BEGINNER -> PrimaryGreen
        LessonDifficulty.INTERMEDIATE -> WarningOrange
        LessonDifficulty.ADVANCED -> ErrorRed
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = categoryColor.copy(alpha = 0.15f),
                modifier = Modifier.size(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isCompleted) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = PrimaryGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            when (lesson.category) {
                                EducationCategory.BASICS -> Icons.Default.MenuBook
                                EducationCategory.GREEKS -> Icons.Default.Functions
                                EducationCategory.STRATEGIES -> Icons.Default.TrendingUp
                                EducationCategory.ADVANCED -> Icons.Default.School
                            },
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lesson.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = lesson.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Difficulty badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = difficultyColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = lesson.difficulty.name,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = difficultyColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    // Duration
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = TextSecondaryDark
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            lesson.duration,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondaryDark
                        )
                    }
                    // Quiz indicator
                    if (lesson.quiz != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Quiz,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = AccentPurple
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Quiz",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentPurple
                            )
                        }
                    }
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondaryDark
            )
        }
    }
}

@Composable
fun GreekCard(
    greek: GreekInfo,
    onClick: () -> Unit
) {
    val greekColor = Color(greek.colorHex)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Greek Symbol
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = greekColor,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = greek.symbol,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greek.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = greek.shortDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondaryDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextSecondaryDark
            )
        }
    }
}

@Composable
fun StrategyCard(
    strategy: OptionStrategy,
    onClick: () -> Unit
) {
    val typeColor = when (strategy.type) {
        EducationStrategyType.BULLISH -> PrimaryGreen
        EducationStrategyType.BEARISH -> ErrorRed
        EducationStrategyType.NEUTRAL -> AccentBlue
        EducationStrategyType.VOLATILE -> WarningOrange
    }

    val riskColor = when (strategy.riskLevel) {
        EducationRiskLevel.LOW -> PrimaryGreen
        EducationRiskLevel.MEDIUM -> WarningOrange
        EducationRiskLevel.HIGH -> ErrorRed
        EducationRiskLevel.VERY_HIGH -> Color(0xFFFF0000)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with name and risk badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strategy.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = riskColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = strategy.riskLevel.name.replace("_", " "),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = riskColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Tags
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Type tag
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = typeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = strategy.type.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = typeColor,
                        fontWeight = FontWeight.Medium
                    )
                }
                // Legs count
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = TextSecondaryDark.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${strategy.legs.size} Legs",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondaryDark,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = strategy.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondaryDark,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Arrow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondaryDark
                )
            }
        }
    }
}
