package com.optix.app.presentation.screens.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.*

// iOS-matching gradient colors
private val GradientStart = Color(0xFF6366F1)
private val GradientMid = Color(0xFF8B5CF6)
private val GradientEnd = Color(0xFFA78BFA)
private val ChatGradient = Brush.horizontalGradient(listOf(GradientStart, GradientMid, GradientEnd))
private val HeaderGradient = Brush.verticalGradient(
    listOf(Color(0xFF7C3AED), Color(0xFF8B5CF6), Color(0xFFA78BFA))
)

// Colors
private val PurpleAccent = Color(0xFF6366F1)
private val LightPurple = Color(0xFFE0E7FF)
private val SendButtonActive = Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFFA78BFA)))
private val SendButtonInactive = Color(0xFFD1D5DB)
private val CardBackground = Color(0xFFFAFAFA)
private val BubbleBorder = Color(0xFFE5E7EB)

// Finance terms to highlight
private val financeTerms = listOf(
    "IV", "PCR", "OI", "ATM", "ITM", "OTM", "NIFTY", "BANKNIFTY",
    "Delta", "Gamma", "Theta", "Vega", "Greeks", "VIX",
    "Call", "Put", "Strike", "Expiry", "Premium", "Straddle", "Strangle",
    "Iron Condor", "Butterfly", "Spread", "Max Pain"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    initialQuery: String = ""
) {
    val state by viewModel.state.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Send initial query if provided (from Ask AI button)
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            viewModel.sendMessage(initialQuery)
        }
    }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ChatEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(
                        message = event.message,
                        duration = SnackbarDuration.Short
                    )
                }
                is ChatEvent.ScrollToBottom -> {
                    if (state.messages.isNotEmpty()) {
                        listState.animateScrollToItem(state.messages.size - 1)
                    }
                }
                is ChatEvent.SessionDeleted -> {
                    snackbarHostState.showSnackbar("Session deleted")
                }
            }
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color(0xFFF5F5F5)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5))
        ) {
            // Gradient header
            ChatHeader(onBack = onBack, onClear = { viewModel.clearChat() })

            // Messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Suggested questions (show when no user messages yet)
                if (state.messages.size <= 1) {
                    item {
                        SuggestedQuestionsSection(
                            questions = state.suggestedQuestions,
                            onQuestionClick = { viewModel.askSuggestedQuestion(it) }
                        )
                    }
                }

                items(state.messages, key = { it.id }) { message ->
                    ChatMessageBubble(message = message)
                }

                // Typing indicator
                if (state.isTyping) {
                    item {
                        TypingIndicatorView()
                    }
                }
            }

            // Input field
            ChatInputView(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        viewModel.sendMessage(inputText)
                        inputText = ""
                    }
                },
                isEnabled = !state.isTyping
            )
        }
    }
}

@Composable
private fun ChatHeader(
    onBack: () -> Unit,
    onClear: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderGradient)
    ) {
        // Decorative circles in background
        Box(
            modifier = Modifier
                .size(90.dp)
                .offset(x = (-30).dp, y = (-40).dp)
                .background(Color.White.copy(alpha = 0.08f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(80.dp)
                .offset(x = 300.dp, y = 10.dp)
                .background(Color.White.copy(alpha = 0.06f), CircleShape)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Close button
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.15f),
                onClick = onBack
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Title with icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // AI Avatar
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Column {
                    Text(
                        text = "Optixia",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4ADE80))
                        )
                        Text(
                            text = "Online",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }

            // Refresh button
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.15f),
                onClick = onClear
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Clear chat",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestedQuestionsSection(
    questions: List<String>,
    onQuestionClick: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFF3E0)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = "Suggested Questions",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF374151)
            )
        }

        questions.forEachIndexed { index, question ->
            val (icon, iconColor, bgColor) = when (index % 4) {
                0 -> Triple(Icons.Default.TrendingUp, Color(0xFF10B981), Color(0xFFD1FAE5))
                1 -> Triple(Icons.Default.BarChart, Color(0xFF3B82F6), Color(0xFFDBEAFE))
                2 -> Triple(Icons.Default.Psychology, Color(0xFF8B5CF6), Color(0xFFEDE9FE))
                else -> Triple(Icons.Default.QuestionAnswer, Color(0xFFF59E0B), Color(0xFFFEF3C7))
            }

            SuggestedQuestionCard(
                question = question,
                icon = icon,
                iconColor = iconColor,
                bgColor = bgColor,
                onClick = { onQuestionClick(question) }
            )
        }
    }
}

@Composable
private fun SuggestedQuestionCard(
    question: String,
    icon: ImageVector,
    iconColor: Color,
    bgColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = question,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF374151),
                modifier = Modifier.weight(1f),
                lineHeight = 20.sp
            )

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Color(0xFFD1D5DB),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ChatMessageBubble(message: ChatMessage) {
    val isUser = message.isFromUser

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            // User message - gradient bubble
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .shadow(4.dp, RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                    .clip(RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp))
                    .background(ChatGradient)
            ) {
                Text(
                    text = message.content,
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        } else {
            // Assistant message - white card with border
            Surface(
                modifier = Modifier
                    .widthIn(max = 340.dp)
                    .shadow(3.dp, RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp)),
                shape = RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp),
                color = Color.White
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Header with icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = PurpleAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            "Optixia",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PurpleAccent
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    // Parsed content
                    SmartTextContent(
                        text = message.content,
                        textColor = Color(0xFF374151)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatTime(message.timestamp),
            fontSize = 10.sp,
            color = Color(0xFF9CA3AF),
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

/**
 * Smart text view that parses markdown-style content like iOS
 */
@Composable
private fun SmartTextContent(
    text: String,
    textColor: Color
) {
    val blocks = parseContentBlocks(text)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is ContentBlock.Header -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(PurpleAccent, Color(0xFF8B5CF6))
                                    )
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = block.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F2937),
                            letterSpacing = 0.3.sp
                        )
                    }
                }
                is ContentBlock.SubHeader -> {
                    Text(
                        text = highlightedText(block.text, textColor),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF374151)
                    )
                }
                is ContentBlock.BulletList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(PurpleAccent)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = highlightedText(item, textColor),
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
                is ContentBlock.NumberedList -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(LightPurple),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PurpleAccent
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = highlightedText(item, textColor),
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }
                        }
                    }
                }
                is ContentBlock.Paragraph -> {
                    if (block.text.isNotBlank()) {
                        Text(
                            text = highlightedText(block.text, textColor),
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Parse content into blocks (like iOS SmartTextView)
 */
private fun parseContentBlocks(text: String): List<ContentBlock> {
    val blocks = mutableListOf<ContentBlock>()
    val lines = text.split("\n")
    var i = 0

    while (i < lines.size) {
        val line = lines[i].trim()

        when {
            // Empty line - skip
            line.isEmpty() -> {
                i++
            }
            // Header: starts with **text** and nothing else, or ends with **
            line.startsWith("**") && line.endsWith("**") && line.count { it == '*' } == 4 -> {
                blocks.add(ContentBlock.Header(line.removeSurrounding("**")))
                i++
            }
            // Sub-header: **text:** pattern
            line.startsWith("**") && line.contains(":**") -> {
                blocks.add(ContentBlock.SubHeader(line.replace("**", "")))
                i++
            }
            // Bullet list
            line.startsWith("- ") || line.startsWith("• ") || line.startsWith("* ") -> {
                val bulletItems = mutableListOf<String>()
                while (i < lines.size) {
                    val bulletLine = lines[i].trim()
                    if (bulletLine.startsWith("- ") || bulletLine.startsWith("• ") || bulletLine.startsWith("* ")) {
                        bulletItems.add(parseBoldInline(bulletLine.drop(2)))
                        i++
                    } else {
                        break
                    }
                }
                if (bulletItems.isNotEmpty()) {
                    blocks.add(ContentBlock.BulletList(bulletItems))
                }
            }
            // Numbered list
            line.matches(Regex("^\\d+\\.\\s.*")) -> {
                val numberedItems = mutableListOf<String>()
                while (i < lines.size) {
                    val numberedLine = lines[i].trim()
                    if (numberedLine.matches(Regex("^\\d+\\.\\s.*"))) {
                        numberedItems.add(parseBoldInline(numberedLine.substringAfter(". ")))
                        i++
                    } else {
                        break
                    }
                }
                if (numberedItems.isNotEmpty()) {
                    blocks.add(ContentBlock.NumberedList(numberedItems))
                }
            }
            // Regular paragraph
            else -> {
                blocks.add(ContentBlock.Paragraph(parseBoldInline(line)))
                i++
            }
        }
    }

    return blocks
}

/**
 * Remove **bold** markers and return clean text (for simple display)
 */
private fun parseBoldInline(text: String): String {
    return text.replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
}

/**
 * Create highlighted annotated string with bold and finance terms
 */
@Composable
private fun highlightedText(text: String, baseColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var remaining = text
        val boldPattern = Regex("\\*\\*(.+?)\\*\\*")

        while (remaining.isNotEmpty()) {
            val match = boldPattern.find(remaining)
            if (match != null) {
                // Text before bold - check for finance terms
                if (match.range.first > 0) {
                    appendWithFinanceHighlight(remaining.substring(0, match.range.first), baseColor)
                }
                // Bold text
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                    append(match.groupValues[1])
                }
                remaining = remaining.substring(match.range.last + 1)
            } else {
                // No more bold - check for finance terms
                appendWithFinanceHighlight(remaining, baseColor)
                break
            }
        }
    }
}

private fun AnnotatedString.Builder.appendWithFinanceHighlight(text: String, baseColor: Color) {
    var currentPos = 0
    val sortedMatches = financeTerms
        .flatMap { term ->
            Regex("\\b${Regex.escape(term)}\\b", RegexOption.IGNORE_CASE)
                .findAll(text)
                .map { it.range to term }
                .toList()
        }
        .sortedBy { it.first.first }

    for ((range, _) in sortedMatches) {
        if (range.first >= currentPos) {
            // Text before match
            if (range.first > currentPos) {
                withStyle(SpanStyle(color = baseColor)) {
                    append(text.substring(currentPos, range.first))
                }
            }
            // Highlighted term - blue color like iOS
            withStyle(SpanStyle(color = Color(0xFF3B82F6), fontWeight = FontWeight.Medium)) {
                append(text.substring(range.first, range.last + 1))
            }
            currentPos = range.last + 1
        }
    }

    // Remaining text
    if (currentPos < text.length) {
        withStyle(SpanStyle(color = baseColor)) {
            append(text.substring(currentPos))
        }
    }
}

sealed class ContentBlock {
    data class Header(val text: String) : ContentBlock()
    data class SubHeader(val text: String) : ContentBlock()
    data class BulletList(val items: List<String>) : ContentBlock()
    data class NumberedList(val items: List<String>) : ContentBlock()
    data class Paragraph(val text: String) : ContentBlock()
}

@Composable
private fun TypingIndicatorView() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // AI Avatar
        Box(
            modifier = Modifier
                .size(36.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFA78BFA)))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        // Typing bubble
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(3) { index ->
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500, delayMillis = index * 150),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot$index"
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(PurpleAccent.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatInputView(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Input field
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 50.dp),
                shape = RoundedCornerShape(25.dp),
                color = Color(0xFFF3F4F6)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 15.sp,
                            color = Color(0xFF374151)
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (isEnabled) onSend() }),
                        singleLine = true,
                        decorationBox = { innerTextField ->
                            Box {
                                if (inputText.isEmpty()) {
                                    Text(
                                        "Ask anything about options...",
                                        fontSize = 15.sp,
                                        color = Color(0xFF9CA3AF)
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Send button - iOS style
            val isActive = inputText.isNotBlank() && isEnabled
            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color = if (isActive) Color.Transparent else Color(0xFFE5E7EB),
                onClick = { if (isActive) onSend() }
            ) {
                Box(
                    modifier = if (isActive) {
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF8B5CF6), Color(0xFFA78BFA))
                                )
                            )
                    } else {
                        Modifier.fillMaxSize()
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        "Send",
                        tint = if (isActive) Color.White else Color(0xFF9CA3AF),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // SEBI Disclaimer
        Text(
            text = "⚠️ AI responses are not investment advice. Investment in securities market is subject to market risks. Consult a SEBI-registered advisor.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
