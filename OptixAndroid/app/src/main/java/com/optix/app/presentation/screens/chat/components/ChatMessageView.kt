package com.optix.app.presentation.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.optix.app.presentation.screens.chat.ChatMessage
import com.optix.app.presentation.theme.*
import java.text.SimpleDateFormat
import java.util.*

private val GradientStart = Color(0xFF6366F1)
private val GradientEnd = Color(0xFF8B5CF6)
private val ChatGradient = Brush.horizontalGradient(listOf(GradientStart, GradientEnd))

// Financial terms to highlight
private val financialTerms = listOf(
    "Delta", "Gamma", "Theta", "Vega", "IV", "PCR", "OI", "ATM", "ITM", "OTM",
    "Call", "Put", "Strike", "Expiry", "Premium", "Straddle", "Strangle",
    "Iron Condor", "Butterfly", "Spread", "Max Pain", "Greeks"
)

/**
 * Smart chat message view with markdown parsing and financial term highlighting
 */
@Composable
fun ChatMessageView(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    val isUser = message.isFromUser
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            if (message.isError) {
                ErrorMessageBubble(message = message)
            } else if (isUser) {
                UserMessageBubble(message = message)
            } else {
                AssistantMessageBubble(message = message)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondaryDark
            )
        }
    }
}

@Composable
private fun UserMessageBubble(message: ChatMessage) {
    Surface(
        shape = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = 20.dp,
            bottomEnd = 4.dp
        ),
        color = Color.Transparent,
        modifier = Modifier.widthIn(max = 280.dp)
    ) {
        Box(modifier = Modifier.background(ChatGradient)) {
            Text(
                text = message.content,
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun AssistantMessageBubble(message: ChatMessage) {
    Surface(
        shape = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = 4.dp,
            bottomEnd = 20.dp
        ),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.widthIn(max = 300.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Assistant header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = GradientStart,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Optixia",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = GradientStart
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            // Parsed message content
            ParsedMessageContent(
                content = message.content,
                textColor = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ErrorMessageBubble(message: ChatMessage) {
    Surface(
        shape = RoundedCornerShape(
            topStart = 20.dp,
            topEnd = 20.dp,
            bottomStart = 4.dp,
            bottomEnd = 20.dp
        ),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.widthIn(max = 280.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message.content,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}

/**
 * Parse and render message content with markdown-like formatting
 */
@Composable
private fun ParsedMessageContent(
    content: String,
    textColor: Color
) {
    val annotatedString = buildAnnotatedString {
        var currentIndex = 0
        val lines = content.split("\n")

        lines.forEachIndexed { lineIndex, line ->
            when {
                // Headers (starts with **)
                line.startsWith("**") && line.endsWith("**") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                        append(line.removeSurrounding("**"))
                    }
                }
                // Headers (ends with :)
                line.endsWith(":") && line.startsWith("**") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp)) {
                        append(parseBoldText(line, textColor))
                    }
                }
                // Bullet points
                line.startsWith("- ") -> {
                    withStyle(SpanStyle(color = GradientStart)) {
                        append("  \u2022 ")
                    }
                    append(parseBoldText(line.removePrefix("- "), textColor))
                }
                // Numbered lists
                line.matches(Regex("^\\d+\\.\\s.*")) -> {
                    val number = line.substringBefore(".")
                    withStyle(SpanStyle(color = GradientStart, fontWeight = FontWeight.Bold)) {
                        append("  $number. ")
                    }
                    append(parseBoldText(line.substringAfter(". "), textColor))
                }
                // Regular text
                else -> {
                    append(parseBoldText(line, textColor))
                }
            }

            if (lineIndex < lines.size - 1) {
                append("\n")
            }
        }
    }

    Text(
        text = annotatedString,
        color = textColor,
        fontSize = 15.sp,
        lineHeight = 22.sp
    )
}

/**
 * Parse text for bold sections and financial terms
 */
private fun parseBoldText(text: String, textColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var remaining = text
        var lastIndex = 0

        // Find and style **bold** text
        val boldPattern = Regex("\\*\\*(.+?)\\*\\*")
        val matches = boldPattern.findAll(remaining)

        matches.forEach { match ->
            // Append text before the match
            if (match.range.first > lastIndex) {
                val beforeText = remaining.substring(lastIndex, match.range.first)
                append(highlightFinancialTerms(beforeText, textColor))
            }

            // Append bold text
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(match.groupValues[1])
            }

            lastIndex = match.range.last + 1
        }

        // Append remaining text
        if (lastIndex < remaining.length) {
            append(highlightFinancialTerms(remaining.substring(lastIndex), textColor))
        }
    }
}

/**
 * Highlight financial terms in text
 */
private fun highlightFinancialTerms(text: String, textColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var currentText = text
        var searchStart = 0

        while (searchStart < currentText.length) {
            var foundTerm: String? = null
            var foundIndex = currentText.length

            // Find the earliest occurring financial term
            for (term in financialTerms) {
                val index = currentText.indexOf(term, searchStart, ignoreCase = true)
                if (index != -1 && index < foundIndex) {
                    foundIndex = index
                    foundTerm = term
                }
            }

            if (foundTerm != null && foundIndex < currentText.length) {
                // Append text before the term
                if (foundIndex > searchStart) {
                    append(currentText.substring(searchStart, foundIndex))
                }

                // Append highlighted term
                withStyle(
                    SpanStyle(
                        color = GradientStart,
                        fontWeight = FontWeight.Medium
                    )
                ) {
                    append(currentText.substring(foundIndex, foundIndex + foundTerm.length))
                }

                searchStart = foundIndex + foundTerm.length
            } else {
                // No more terms found, append remaining text
                append(currentText.substring(searchStart))
                break
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
