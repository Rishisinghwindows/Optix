package com.optix.app.presentation.screens.education

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optix.app.domain.model.*
import com.optix.app.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    lesson: Lesson,
    onBack: () -> Unit,
    onStartQuiz: (List<QuizQuestion>) -> Unit,
    onComplete: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        lesson.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Quiz button if available
                    if (lesson.quiz != null) {
                        OutlinedButton(
                            onClick = { onStartQuiz(lesson.quiz) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = AccentPurple
                            ),
                            border = BorderStroke(1.dp, AccentPurple)
                        ) {
                            Icon(
                                Icons.Default.Quiz,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Take Quiz", fontWeight = FontWeight.Bold)
                        }
                    }
                    // Complete button
                    Button(
                        onClick = onComplete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Complete", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                LessonHeader(lesson = lesson)
            }

            // Key Points
            if (lesson.keyPoints.isNotEmpty()) {
                item {
                    KeyPointsCard(keyPoints = lesson.keyPoints)
                }
            }

            // Content Sections
            items(lesson.content) { section ->
                LessonSectionCard(section = section)
            }

            // Bottom spacing
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun LessonHeader(lesson: Lesson) {
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = categoryColor.copy(alpha = 0.15f),
                modifier = Modifier.size(60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when (lesson.category) {
                            EducationCategory.BASICS -> Icons.Default.MenuBook
                            EducationCategory.GREEKS -> Icons.Default.Functions
                            EducationCategory.STRATEGIES -> Icons.Default.TrendingUp
                            EducationCategory.ADVANCED -> Icons.Default.School
                        },
                        contentDescription = null,
                        tint = categoryColor,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = lesson.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Difficulty
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = difficultyColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = lesson.difficulty.name,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = difficultyColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Duration
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
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
                                modifier = Modifier.size(14.dp),
                                tint = AccentPurple
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Quiz",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentPurple,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyPointsCard(keyPoints: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = PrimaryGreen.copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, PrimaryGreen.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Key Points",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = PrimaryGreen
            )
            Spacer(modifier = Modifier.height(12.dp))
            keyPoints.forEach { point ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(top = 2.dp),
                        tint = PrimaryGreen
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = point,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondaryDark
                    )
                }
            }
        }
    }
}

@Composable
private fun LessonSectionCard(section: LessonSection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Section Title
            if (section.title != null) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Section Content
            Text(
                text = section.content,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 24.sp,
                color = TextSecondaryDark
            )

            // Example
            if (section.example != null) {
                Spacer(modifier = Modifier.height(16.dp))
                ExampleCard(example = section.example)
            }

            // Tip
            if (section.tip != null) {
                Spacer(modifier = Modifier.height(16.dp))
                TipCard(tip = section.tip)
            }
        }
    }
}

@Composable
private fun ExampleCard(example: LessonExample) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = WarningOrange.copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, WarningOrange.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = WarningOrange
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = example.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = WarningOrange
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Scenario
            Text(
                text = example.scenario,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondaryDark
            )

            // Calculation
            if (example.calculation != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Text(
                        text = example.calculation,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Result
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = example.result,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = PrimaryGreen
            )
        }
    }
}

@Composable
private fun TipCard(tip: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = AccentBlue.copy(alpha = 0.1f)
        ),
        border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.TipsAndUpdates,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = AccentBlue
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Pro Tip",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AccentBlue
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tip,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondaryDark
                )
            }
        }
    }
}
