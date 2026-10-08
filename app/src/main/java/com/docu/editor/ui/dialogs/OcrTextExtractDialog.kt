package com.docu.editor.ui.dialogs

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.docu.editor.core.ocr.model.DetectedTextItem
import com.docu.editor.core.ocr.util.DocumentTranslationEngine
import com.docu.editor.core.ocr.util.RichMarkdownConverter
import com.docu.editor.core.ocr.util.TranslatedDocumentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * Enterprise CamScanner-Grade OCR Text Extraction & Accessibility Dialog.
 *
 * PRO Superpowers:
 * 1. Rich Markdown Copy: Preserves headings, Markdown tables, lists, and bold keys.
 * 2. Karaoke Text-to-Speech: Android TTS reader with live spoken sentence highlighting and speech rate controls.
 * 3. In-Place Document Translation Overlay: Translates text directly over the document layout and exports translated PDF.
 */
@Composable
fun OcrTextExtractDialog(
    detectedItems: List<DetectedTextItem>,
    currentBitmap: Bitmap? = null,
    geminiApiKey: String = "",
    onCopyAll: (String) -> Unit,
    onShareTxt: (String) -> Unit,
    onShareCsv: (String) -> Unit = {},
    onShareDocx: (String) -> Unit = {},
    onHandwritingAiRequest: ((onComplete: (String?) -> Unit) -> Unit)? = null,
    onOfflineHandwritingRequest: ((onComplete: (String?) -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Plain text reconstruction
    val reconstructedText = remember(detectedItems) {
        if (detectedItems.isEmpty()) {
            "No text recognized on this page. Tap 'Scan' or ensure lighting is clear."
        } else {
            val sorted = detectedItems.sortedWith(
                compareBy<DetectedTextItem> { it.boundingBox.top / 20 }
                    .thenBy { it.boundingBox.left }
            )
            val sb = StringBuilder()
            var lastTop = -1
            for (item in sorted) {
                if (lastTop != -1 && kotlin.math.abs(item.boundingBox.top - lastTop) > item.boundingBox.height() * 0.8f) {
                    sb.append("\n")
                } else if (lastTop != -1) {
                    sb.append(" ")
                }
                sb.append(item.text)
                lastTop = item.boundingBox.top
            }
            sb.toString()
        }
    }

    // Rich Markdown conversion
    val richMarkdownText = remember(detectedItems) {
        if (detectedItems.isNotEmpty()) {
            RichMarkdownConverter.convertToMarkdown(detectedItems)
        } else {
            ""
        }
    }

    var editableText by remember(reconstructedText) { mutableStateOf(reconstructedText) }
    var selectedViewMode by remember { mutableIntStateOf(0) } // 0: Plain Text Editor, 1: Rich Markdown, 2: Karaoke TTS, 3: In-Place Translation
    var searchQuery by remember { mutableStateOf("") }

    // TTS state
    var ttsInstance by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var isSpeaking by remember { mutableStateOf(false) }
    var currentSentenceIndex by remember { mutableIntStateOf(-1) }
    var speechRate by remember { mutableFloatStateOf(1.0f) }
    var ttsLanguage by remember { mutableStateOf("English") }

    // Sentences for Karaoke Reader
    val sentences = remember(editableText) {
        editableText.split(Regex("""(?<=[.?!।\n])\s+""")).filter { it.isNotBlank() }
    }
    val karaokeListState = rememberLazyListState()

    // Translation state
    var targetLanguage by remember { mutableStateOf("Hindi (हिन्दी)") }
    var showLangDropdown by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var translationResult by remember { mutableStateOf<TranslatedDocumentResult?>(null) }

    // Initialize TTS
    DisposableEffect(Unit) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isTtsReady = true
            }
        }
        ttsInstance = tts

        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    // Scroll Karaoke list to active sentence
    LaunchedEffect(currentSentenceIndex) {
        if (currentSentenceIndex in sentences.indices) {
            karaokeListState.animateScrollToItem(currentSentenceIndex)
        }
    }

    fun speakSentence(index: Int) {
        val tts = ttsInstance ?: return
        if (index !in sentences.indices) {
            isSpeaking = false
            currentSentenceIndex = -1
            return
        }

        currentSentenceIndex = index
        isSpeaking = true

        val sentenceToSpeak = sentences[index]
        val params = android.os.Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "sentence_$index")
        }

        tts.setSpeechRate(speechRate)
        if (ttsLanguage == "Hindi") {
            tts.language = Locale.forLanguageTag("hi-IN")
        } else {
            tts.language = Locale.US
        }

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                isSpeaking = true
            }

            override fun onDone(utteranceId: String?) {
                scope.launch {
                    val next = index + 1
                    if (next < sentences.size && isSpeaking) {
                        speakSentence(next)
                    } else {
                        isSpeaking = false
                        currentSentenceIndex = -1
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                isSpeaking = false
            }
        })

        tts.speak(sentenceToSpeak, TextToSpeech.QUEUE_FLUSH, params, "sentence_$index")
    }

    fun stopTts() {
        ttsInstance?.stop()
        isSpeaking = false
        currentSentenceIndex = -1
    }

    val wordCount = remember(editableText) {
        editableText.split("\\s+".toRegex()).count { it.isNotBlank() }
    }
    val charCount = remember(editableText) { editableText.length }

    Dialog(
        onDismissRequest = {
            stopTts()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxSize(0.94f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFEFF6FF)
                        ) {
                            Box(
                                modifier = Modifier.padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Extract Text Studio",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFDCFCE7)
                                ) {
                                    Text(
                                        text = "PRO ACCESSIBILITY",
                                        color = Color(0xFF15803D),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "$wordCount words • $charCount characters • ${detectedItems.size} blocks",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = {
                        stopTts()
                        onDismiss()
                    }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Mode Tabs
                TabRow(
                    selectedTabIndex = selectedViewMode,
                    containerColor = Color(0xFFF1F5F9),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedViewMode == 0,
                        onClick = {
                            stopTts()
                            selectedViewMode = 0
                        },
                        text = {
                            Text(
                                "Plain Text",
                                fontSize = 12.sp,
                                fontWeight = if (selectedViewMode == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedViewMode == 0) Color(0xFF2563EB) else Color(0xFF64748B)
                            )
                        }
                    )
                    Tab(
                        selected = selectedViewMode == 1,
                        onClick = {
                            stopTts()
                            selectedViewMode = 1
                        },
                        text = {
                            Text(
                                "Rich Markdown",
                                fontSize = 12.sp,
                                fontWeight = if (selectedViewMode == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedViewMode == 1) Color(0xFF2563EB) else Color(0xFF64748B)
                            )
                        }
                    )
                    Tab(
                        selected = selectedViewMode == 2,
                        onClick = { selectedViewMode = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(13.dp), tint = if (selectedViewMode == 2) Color(0xFF16A34A) else Color(0xFF64748B))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    "Karaoke TTS",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedViewMode == 2) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedViewMode == 2) Color(0xFF16A34A) else Color(0xFF64748B)
                                )
                            }
                        }
                    )
                    Tab(
                        selected = selectedViewMode == 3,
                        onClick = {
                            stopTts()
                            selectedViewMode = 3
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(13.dp), tint = if (selectedViewMode == 3) Color(0xFF7C3AED) else Color(0xFF64748B))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    "Translate",
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedViewMode == 3) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedViewMode == 3) Color(0xFF7C3AED) else Color(0xFF64748B)
                                )
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // VIEW MODE 0: PLAIN TEXT
                if (selectedViewMode == 0) {
                    val matchCount = remember(searchQuery, editableText) {
                        if (searchQuery.isBlank()) 0
                        else Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE).findAll(editableText).count()
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search word in extracted text...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 6.dp)) {
                                    Surface(shape = RoundedCornerShape(6.dp), color = if (matchCount > 0) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)) {
                                        Text(text = "$matchCount found", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (matchCount > 0) Color(0xFF15803D) else Color(0xFFB91C1C), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(22.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                            OutlinedTextField(
                                value = editableText,
                                onValueChange = { editableText = it },
                                modifier = Modifier.fillMaxSize(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedBorderColor = Color.Transparent
                                )
                            )
                        }
                    }
                }

                // VIEW MODE 1: RICH MARKDOWN PREVIEW
                else if (selectedViewMode == 1) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.dp, Color(0xFF334155)),
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                "RICH GITHUB-FLAVORED MARKDOWN",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (richMarkdownText.isNotBlank()) richMarkdownText else RichMarkdownConverter.convertPlainTextToMarkdown(editableText),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFFE2E8F0),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // VIEW MODE 2: KARAOKE TEXT-TO-SPEECH
                else if (selectedViewMode == 2) {
                    // TTS Audio Controller Bar
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSpeaking) {
                                    IconButton(
                                        onClick = { stopTts() },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEF4444))
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                } else {
                                    IconButton(
                                        onClick = { speakSentence(if (currentSentenceIndex != -1) currentSentenceIndex else 0) },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF16A34A))
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        if (isSpeaking) "Reading Aloud (${currentSentenceIndex + 1}/${sentences.size})..." else "Ready to Read Aloud",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF14532D)
                                    )
                                    Text(
                                        "Karaoke Sentence Highlighter",
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF16A34A)
                                    )
                                }
                            }

                            // Speed Selector Chips
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f)
                                for (sp in speeds) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (speechRate == sp) Color(0xFF16A34A) else Color(0xFFDCFCE7),
                                        modifier = Modifier.clickable {
                                            speechRate = sp
                                            ttsInstance?.setSpeechRate(sp)
                                        }
                                    ) {
                                        Text(
                                            "${sp}x",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (speechRate == sp) Color.White else Color(0xFF15803D),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Karaoke Sentences List
                    LazyColumn(
                        state = karaokeListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        itemsIndexed(sentences) { idx, sentence ->
                            val isCurrent = idx == currentSentenceIndex
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCurrent) Color(0xFFFEF08A) else Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, if (isCurrent) Color(0xFFFACC15) else Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clickable {
                                        speakSentence(idx)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isCurrent) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.VolumeUp,
                                            contentDescription = null,
                                            tint = Color(0xFF854D0E),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = sentence,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) Color(0xFF713F12) else Color(0xFF1E293B),
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // VIEW MODE 3: IN-PLACE DOCUMENT TRANSLATION OVERLAY
                else if (selectedViewMode == 3) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Language Selector Header Bar
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFFFAF5FF),
                            border = BorderStroke(1.dp, Color(0xFFE9D5FF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Translate, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Target: $targetLanguage",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF581C87)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Box {
                                        OutlinedButton(
                                            onClick = { showLangDropdown = true },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Change Language", fontSize = 11.sp)
                                        }
                                        DropdownMenu(
                                            expanded = showLangDropdown,
                                            onDismissRequest = { showLangDropdown = false }
                                        ) {
                                            for ((eng, native) in DocumentTranslationEngine.SUPPORTED_LANGUAGES) {
                                                DropdownMenuItem(
                                                    text = { Text("$eng ($native)") },
                                                    onClick = {
                                                        targetLanguage = "$eng ($native)"
                                                        showLangDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            if (currentBitmap != null && detectedItems.isNotEmpty()) {
                                                isTranslating = true
                                                scope.launch {
                                                    val res = DocumentTranslationEngine.translateAndOverlay(
                                                        sourceBitmap = currentBitmap,
                                                        items = detectedItems,
                                                        targetLanguage = targetLanguage,
                                                        apiKey = geminiApiKey
                                                    )
                                                    translationResult = res
                                                    isTranslating = false
                                                }
                                            } else {
                                                Toast.makeText(context, "Document image and text items required for overlay", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        if (isTranslating) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Translating...", fontSize = 11.sp)
                                        } else {
                                            Text("Translate Overlay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Translated Document Preview or Instructions
                        val trans = translationResult
                        if (trans != null) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF0F172A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = trans.translatedBitmap.asImageBitmap(),
                                        contentDescription = "Translated Document Overlay",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 1-Tap Export Translated PDF
                            Button(
                                onClick = {
                                    scope.launch {
                                        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                        val outFile = File(downloadsDir, "Translated_${System.currentTimeMillis()}.pdf")
                                        withContext(Dispatchers.IO) {
                                            DocumentTranslationEngine.exportTranslatedPdf(trans.translatedBitmap, outFile)
                                        }
                                        Toast.makeText(context, "Exported: ${outFile.name}", Toast.LENGTH_LONG).show()
                                        try {
                                            val shareUri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                outFile
                                            )
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Translated PDF"))
                                        } catch (_: Exception) {}
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export In-Place Translated PDF", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(20.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Translate, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(42.dp))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("In-Place Document Translation Overlay", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E293B))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Translates all lines into $targetLanguage while perfectly preserving the document's original background, stamps, seals, and layout.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text("Tap 'Translate Overlay' above to begin.", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF7C3AED))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Export Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Copy Formatted Markdown / Plain Text
                    Button(
                        onClick = {
                            val textToCopy = if (selectedViewMode == 1) {
                                if (richMarkdownText.isNotBlank()) richMarkdownText else RichMarkdownConverter.convertPlainTextToMarkdown(editableText)
                            } else {
                                editableText
                            }
                            onCopyAll(textToCopy)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier.weight(1.1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (selectedViewMode == 1) "Copy Markdown" else "Copy Text",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Share TXT
                    OutlinedButton(
                        onClick = { onShareTxt(editableText) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(0.9f).height(44.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = "TXT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Export Word (.docx)
                    Button(
                        onClick = { onShareDocx(editableText) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E40AF)),
                        modifier = Modifier.weight(0.9f).height(44.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = "Word", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // Export CSV
                    Button(
                        onClick = {
                            val csv = convertDetectedItemsToCsv(detectedItems)
                            onShareCsv(csv)
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(text = "CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * CamScanner-Grade Table to Excel (CSV) tabular reconstruction algorithm.
 */
private fun convertDetectedItemsToCsv(items: List<DetectedTextItem>): String {
    if (items.isEmpty()) return ""
    val sortedY = items.sortedBy { it.boundingBox.top }
    val rows = mutableListOf<MutableList<DetectedTextItem>>()

    for (item in sortedY) {
        val matchingRow = rows.find { row ->
            val avgCenterY = row.map { it.boundingBox.centerY() }.average()
            val height = item.boundingBox.height().coerceAtLeast(18)
            kotlin.math.abs(item.boundingBox.centerY() - avgCenterY) <= height * 0.65f
        }
        if (matchingRow != null) {
            matchingRow.add(item)
        } else {
            rows.add(mutableListOf(item))
        }
    }

    rows.sortBy { row -> row.minOf { it.boundingBox.top } }
    val sb = StringBuilder()
    for (row in rows) {
        row.sortBy { it.boundingBox.left }
        val line = row.joinToString(",") { item ->
            val escaped = item.text.replace("\"", "\"\"")
            if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
                "\"$escaped\""
            } else {
                escaped
            }
        }
        sb.append(line).append("\n")
    }
    return sb.toString()
}
