package com.suseoaa.locationspoofer.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.services.core.PoiItem
import com.amap.api.services.poisearch.PoiSearch
import androidx.compose.ui.res.stringResource
import com.suseoaa.locationspoofer.R
import com.suseoaa.locationspoofer.data.model.AppState
import com.suseoaa.locationspoofer.data.model.GithubRelease
import com.suseoaa.locationspoofer.data.model.SavedLocation
import com.suseoaa.locationspoofer.data.model.WifiLoadStatus
import com.suseoaa.locationspoofer.ui.components.AppMapView
import com.suseoaa.locationspoofer.ui.components.AppMapController
import com.suseoaa.locationspoofer.data.model.AppMapType
import com.suseoaa.locationspoofer.ui.components.MapTypeDialog
import androidx.compose.material.icons.rounded.Layers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.suseoaa.locationspoofer.ui.theme.AccentBlue
import com.suseoaa.locationspoofer.ui.theme.AccentGreen
import com.suseoaa.locationspoofer.ui.theme.AccentOrange
import com.suseoaa.locationspoofer.ui.theme.AppColors
import com.suseoaa.locationspoofer.viewmodel.MainViewModel
import com.suseoaa.locationspoofer.BuildConfig
import androidx.compose.runtime.Composable
import com.suseoaa.locationspoofer.ui.theme.*

data class GroupedReleaseNotes(
    val features: List<String>,
    val fixes: List<String>,
    val others: List<String>
)

fun parseAndCategorizeReleaseNotes(releases: List<GithubRelease>): GroupedReleaseNotes {
    val features = mutableListOf<String>()
    val fixes = mutableListOf<String>()
    val others = mutableListOf<String>()

    val featureHeaderKeywords = listOf(
        "feature", "feat", "add", "new", "improve", "optimize", "enhancement",
        "功能", "新增", "特性", "新功能", "优化", "改进", "提速", "增强"
    )
    val fixHeaderKeywords = listOf(
        "fix", "bug", "crash", "issue", "solve", "repair",
        "修复", "解决", "崩溃", "问题", "纠正", "故障"
    )

    for (release in releases) {
        val lines = release.body.split("\n")
        var currentSection = "other" // "feature", "fix", "other"

        for (line in lines) {
            val cleanLine = line.trim()
            if (cleanLine.isEmpty()) continue

            if (cleanLine.startsWith("#")) {
                val headingText = cleanLine.replace(Regex("^#+\\s*"), "").lowercase()

                // 根据标题关键字确定部分
                if (featureHeaderKeywords.any { headingText.contains(it) }) {
                    currentSection = "feature"
                } else if (fixHeaderKeywords.any { headingText.contains(it) }) {
                    currentSection = "fix"
                } else {
                    currentSection = "other"
                }
                continue
            }

            // 检查它是否为列表项
            val isListItem =
                cleanLine.startsWith("- ") || cleanLine.startsWith("* ") || cleanLine.startsWith("+ ") || cleanLine.matches(
                    Regex("""^\d+\.\s+.*""")
                )

            if (isListItem) {
                // 移除列表前缀
                var itemContent = cleanLine
                if (cleanLine.startsWith("- ") || cleanLine.startsWith("* ") || cleanLine.startsWith(
                        "+ "
                    )
                ) {
                    itemContent = cleanLine.substring(2).trim()
                } else {
                    itemContent = cleanLine.replace(Regex("""^\d+\.\s*"""), "").trim()
                }

                if (itemContent.isEmpty()) continue

                val itemLower = itemContent.lowercase()
                var category = currentSection

                if (category == "other") {
                    if (fixHeaderKeywords.any { itemLower.contains(it) }) {
                        category = "fix"
                    } else if (featureHeaderKeywords.any { itemLower.contains(it) }) {
                        category = "feature"
                    }
                }

                when (category) {
                    "feature" -> features.add(itemContent)
                    "fix" -> fixes.add(itemContent)
                    else -> others.add(itemContent)
                }
            } else {
                if (!cleanLine.startsWith("```") && !cleanLine.startsWith(">")) {
                    others.add(cleanLine)
                }
            }
        }
    }

    return GroupedReleaseNotes(
        features = features.distinct(),
        fixes = fixes.distinct(),
        others = others.distinct()
    )
}

fun generateMergedMarkdown(
    context: android.content.Context,
    grouped: GroupedReleaseNotes
): String {
    val sb = java.lang.StringBuilder()

    if (grouped.features.isNotEmpty()) {
        sb.append("### 🌟 ").append(context.getString(R.string.features_header)).append("\n")
        grouped.features.forEach { item ->
            sb.append("- ").append(item).append("\n")
        }
        sb.append("\n")
    }

    if (grouped.fixes.isNotEmpty()) {
        sb.append("### 🛠 ").append(context.getString(R.string.fixes_header)).append("\n")
        grouped.fixes.forEach { item ->
            sb.append("- ").append(item).append("\n")
        }
        sb.append("\n")
    }

    if (grouped.others.isNotEmpty()) {
        sb.append("### 📝 ").append(context.getString(R.string.others_header)).append("\n")
        grouped.others.forEach { item ->
            if (item.length < 100) {
                sb.append("- ").append(item).append("\n")
            } else {
                sb.append(item).append("\n\n")
            }
        }
    }

    return sb.toString().trim()
}

fun isNewerVersion(versionStr: String, currentStr: String): Boolean {
    val v1 = versionStr.lowercase().removePrefix("v").trim()
    val v2 = currentStr.lowercase().removePrefix("v").trim()

    val parts1 = v1.split(".")
    val parts2 = v2.split(".")

    val length = maxOf(parts1.size, parts2.size)
    for (i in 0 until length) {
        val p1 = parts1.getOrNull(i)?.toIntOrNull() ?: 0
        val p2 = parts2.getOrNull(i)?.toIntOrNull() ?: 0
        if (p1 > p2) return true
        if (p1 < p2) return false
    }
    return false
}


@Composable
fun parseMarkdown(text: String): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split("\n")
        var isInCodeBlock = false

        lines.forEachIndexed { index, line ->
            val cleanLine = line.trim()

            // 检查代码块边界
            if (cleanLine.startsWith("```")) {
                isInCodeBlock = !isInCodeBlock
                return@forEachIndexed
            }

            if (isInCodeBlock) {
                withStyle(
                    style = SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        color = AccentBlue
                    )
                ) {
                    append("    ")
                    append(line)
                }
            } else {
                val headerMatch = Regex("""^(#{1,6})\s+(.*)""").matchEntire(cleanLine)
                if (headerMatch != null) {
                    val hashCount = headerMatch.groupValues[1].length
                    val rest = headerMatch.groupValues[2]
                    val fontSize = when (hashCount) {
                        1 -> 18.sp
                        2 -> 16.sp
                        3 -> 14.sp
                        4 -> 13.sp
                        else -> 12.sp
                    }
                    val fontWeight = FontWeight.Bold
                    val fontStyle = if (hashCount >= 6) FontStyle.Italic else FontStyle.Normal

                    withStyle(
                        style = SpanStyle(
                            fontWeight = fontWeight,
                            fontStyle = fontStyle,
                            fontSize = fontSize,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    ) {
                        parseInlineFormatting(rest)
                    }
                } else if (cleanLine.startsWith(">")) {
                    val rest =
                        if (cleanLine.startsWith("> ")) cleanLine.substring(2) else cleanLine.substring(
                            1
                        )
                    withStyle(
                        style = SpanStyle(
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    ) {
                        append("▎ ")
                        parseInlineFormatting(rest)
                    }
                } else if (cleanLine.startsWith("- ") || cleanLine.startsWith("* ") || cleanLine.startsWith(
                        "+ "
                    )
                ) {
                    append("  • ")
                    val rest = cleanLine.substring(2).trim()
                    parseInlineFormatting(rest)
                } else if (cleanLine.matches(Regex("""^\d+\.\s+.*"""))) {
                    val matchResult = Regex("""^(\d+\.\s+)(.*)""").find(cleanLine)
                    if (matchResult != null) {
                        val prefix = matchResult.groupValues[1]
                        val rest = matchResult.groupValues[2]
                        append("  $prefix")
                        parseInlineFormatting(rest)
                    } else {
                        parseInlineFormatting(cleanLine)
                    }
                } else {
                    parseInlineFormatting(cleanLine)
                }
            }

            if (index < lines.lastIndex) {
                append("\n")
            }
        }
    }
}

@Composable
private fun androidx.compose.ui.text.AnnotatedString.Builder.parseInlineFormatting(text: String) {
    var i = 0
    while (i < text.length) {
        var tokenType = ""
        var minIdx = Int.MAX_VALUE

        val bold1 = text.indexOf("**", i)
        val bold2 = text.indexOf("__", i)
        val italic1 = text.indexOf("*", i)
        val italic2 = text.indexOf("_", i)
        val code = text.indexOf("`", i)
        val strike = text.indexOf("~~", i)
        val link = text.indexOf("[", i)

        if (bold1 in i until minIdx) {
            minIdx = bold1; tokenType = "bold1"
        }
        if (bold2 in i until minIdx) {
            minIdx = bold2; tokenType = "bold2"
        }
        if (italic1 in i until minIdx && bold1 != italic1) {
            minIdx = italic1; tokenType = "italic1"
        }
        if (italic2 in i until minIdx && bold2 != italic2) {
            minIdx = italic2; tokenType = "italic2"
        }
        if (code in i until minIdx) {
            minIdx = code; tokenType = "code"
        }
        if (strike in i until minIdx) {
            minIdx = strike; tokenType = "strike"
        }
        if (link in i until minIdx) {
            minIdx = link; tokenType = "link"
        }

        if (minIdx == Int.MAX_VALUE) {
            append(text.substring(i))
            break
        }

        if (minIdx > i) {
            append(text.substring(i, minIdx))
        }

        i = minIdx
        var parsed = false

        when (tokenType) {
            "bold1", "bold2" -> {
                val delim = if (tokenType == "bold1") "**" else "__"
                val end = text.indexOf(delim, i + 2)
                if (end != -1) {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                        parseInlineFormatting(text.substring(i + 2, end))
                    }
                    i = end + 2
                    parsed = true
                }
            }

            "italic1", "italic2" -> {
                val delim = if (tokenType == "italic1") "*" else "_"
                val end = text.indexOf(delim, i + 1)
                if (end != -1) {
                    withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                        parseInlineFormatting(text.substring(i + 1, end))
                    }
                    i = end + 1
                    parsed = true
                }
            }

            "code" -> {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(
                        style = SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            color = AccentBlue
                        )
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    parsed = true
                }
            }

            "strike" -> {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    withStyle(style = SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        parseInlineFormatting(text.substring(i + 2, end))
                    }
                    i = end + 2
                    parsed = true
                }
            }

            "link" -> {
                val closeBracket = text.indexOf("]", i + 1)
                if (closeBracket != -1) {
                    val openParen = closeBracket + 1
                    if (openParen < text.length && text[openParen] == '(') {
                        val closeParen = text.indexOf(")", openParen + 1)
                        if (closeParen != -1) {
                            val linkText = text.substring(i + 1, closeBracket)
                            val linkUrl = text.substring(openParen + 1, closeParen)

                            withStyle(
                                style = SpanStyle(
                                    color = AccentBlue,
                                    textDecoration = TextDecoration.Underline
                                )
                            ) {
                                pushStringAnnotation(tag = "URL", annotation = linkUrl)
                                parseInlineFormatting(linkText)
                                pop()
                            }
                            i = closeParen + 1
                            parsed = true
                        }
                    }
                }
            }
        }

        if (!parsed) {
            append(text[i])
            i++
        }
    }
}
