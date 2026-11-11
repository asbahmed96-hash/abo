package com.abonesma.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        setContent { AppScreen(intent?.data) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(sharedUri: Uri?) {
    var arabic by remember { mutableStateOf(true) }
    var pdfText by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(if (arabic) "جاهز لاستيراد ملف PDF" else "Ready to import a PDF") }
    var tab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    fun importPdf(uri: Uri) {
        scope.launch {
            status = if (arabic) "جارٍ استخراج النص..." else "Extracting text..."
            try {
                val extracted = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Cannot open PDF" }
                        PDDocument.load(input).use { doc ->
                            PDFTextStripper().getText(doc)
                        }
                    }
                }
                pdfText = extracted
                fileName = uri.lastPathSegment ?: "document.pdf"
                LocalStore.save(context.filesDir, fileName, extracted)
                status = if (extracted.isBlank()) {
                    if (arabic) "لم يتم العثور على نص. قد يكون الملف مصورًا ويحتاج OCR." else "No text found. This may be a scanned PDF and needs OCR."
                } else {
                    if (arabic) "تم استخراج النص وحفظه على الهاتف." else "Text extracted and saved on this device."
                }
            } catch (e: Exception) {
                status = (if (arabic) "تعذر قراءة الملف: " else "Could not read PDF: ") + (e.message ?: "Unknown error")
            }
        }
    }

    LaunchedEffect(sharedUri) { sharedUri?.let(::importPdf) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(::importPdf) }
    val sections = remember(pdfText) { ChapterParser.parse(pdfText) }
    val activeChapter = sections.getOrNull((tab - 1).coerceAtLeast(0))
    val dark = MaterialTheme.colorScheme

    MaterialTheme(colorScheme = dark) {
        Scaffold(topBar = {
            TopAppBar(title = { Text("🦅 ABO NESMA") }, actions = {
                TextButton(onClick = { arabic = !arabic }) { Text(if (arabic) "English" else "العربية") }
            })
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text(if (arabic) "مساعدك الذكي للكتب وملفات PDF" else "Your smart PDF study assistant",
                    style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { picker.launch(arrayOf("application/pdf")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (arabic) "استيراد ملف PDF" else "Import PDF")
                }
                Text(status, Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodySmall)
                if (fileName.isNotBlank()) Text(fileName, style = MaterialTheme.typography.labelLarge)
                if (pdfText.isNotBlank()) {
                    TabRow(selectedTabIndex = tab.coerceIn(0, sections.size)) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(if (arabic) "نظرة عامة" else "Overview") })
                        sections.take(4).forEachIndexed { index, chapter ->
                            Tab(selected = tab == index + 1, onClick = { tab = index + 1 }, text = { Text(chapter.title.take(12)) })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (tab == 0) {
                        Text(if (arabic) "ملخص أولي" else "Initial summary", style = MaterialTheme.typography.titleMedium)
                        Text(Summarizer.summarize(pdfText, 5), Modifier.weight(1f).padding(top = 6.dp))
                    } else if (activeChapter != null) {
                        LazyColumn(Modifier.weight(1f)) {
                            item { Text(activeChapter.title, style = MaterialTheme.typography.titleLarge) }
                            item { Text(if (arabic) "ملخص الدرس" else "Lesson summary", style = MaterialTheme.typography.titleMedium) }
                            item { Text(Summarizer.summarize(activeChapter.body, 5)) }
                            item { Text(if (arabic) "مصطلحات وبطاقات أولية" else "Initial terms and flashcards", style = MaterialTheme.typography.titleMedium) }
                            items(FlashcardMaker.make(activeChapter.body, 12)) { card ->
                                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(card.question, style = MaterialTheme.typography.titleSmall)
                                        Text(card.answer)
                                    }
                                }
                            }
                            item { Text(if (arabic) "بنك أسئلة مبدئي" else "Starter question bank", style = MaterialTheme.typography.titleMedium) }
                            items(QuizMaker.make(activeChapter.body)) { q ->
                                Text("• ${q.prompt}\n${q.answer}", Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(12.dp))
                    Text(if (arabic) "استورد ملفًا للبدء. يدعم الإصدار الحالي ملفات PDF النصية؛ ملفات الصور تحتاج إضافة OCR." else "Import a file to begin. This build currently supports text PDFs; image-only PDFs need OCR.")
                }
            }
        }
    }
}

private object LocalStore {
    fun save(dir: File, name: String, text: String) {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
        File(dir, "$safe.txt").writeText(text, Charsets.UTF_8)
    }
}

data class Chapter(val title: String, val body: String)
private object ChapterParser {
    private val heading = Regex("(?im)^\\s*(chapter|unit|الفصل|الوحدة)\\s*([0-9٠-٩]+)?[^\\n]*$")
    fun parse(text: String): List<Chapter> {
        val matches = heading.findAll(text).toList()
        if (matches.isEmpty()) return listOf(Chapter("Chapter 1", text))
        return matches.mapIndexed { i, m ->
            val end = matches.getOrNull(i + 1)?.range?.first ?: text.length
            Chapter(m.value.trim().ifBlank { "Chapter ${i + 1}" }, text.substring(m.range.last + 1, end).trim())
        }.filter { it.body.isNotBlank() }
    }
}
private object Summarizer {
    fun summarize(text: String, maxSentences: Int): String {
        val sentences = text.replace(Regex("\\s+"), " ").split(Regex("(?<=[.!?؟])\\s+"))
            .map { it.trim() }.filter { it.length > 35 }
        if (sentences.isEmpty()) return text.take(900)
        val keywords = text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 4 }.groupingBy { it }.eachCount()
        return sentences.sortedByDescending { s ->
            s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).sumOf { keywords[it] ?: 0 }
        }.take(maxSentences).joinToString(" ")
    }
}
data class Flashcard(val question: String, val answer: String)
private object FlashcardMaker {
    fun make(text: String, limit: Int): List<Flashcard> {
        val lines = text.lines().map { it.trim() }.filter { it.length in 12..180 }.distinct()
        return lines.take(limit).map { line ->
            val term = line.substringBefore(":").takeIf { it.length in 3..70 }
            if (term != null && term.length < line.length - 3)
                Flashcard("What is $term?", line.substringAfter(":").trim())
            else Flashcard("Complete / explain: ${line.take(90)}…", line)
        }
    }
}
data class QuizItem(val prompt: String, val answer: String, val type: String)
private object QuizMaker {
    fun make(text: String): List<QuizItem> {
        val lines = text.lines().map { it.trim() }.filter { it.length in 35..220 }.distinct()
        val mcq = lines.take(20).map { QuizItem("MCQ: Which statement matches the source?\n${it.take(160)}", "Correct statement: see source text.", "MCQ") }
        val tf = lines.drop(20).take(10).map { QuizItem("True or False: ${it.take(160)}", "Verify against the source text.", "TF") }
        val essay = lines.drop(30).take(5).map { QuizItem("Explain in your own words: ${it.take(120)}", "Open response; compare with the source.", "Essay") }
        return mcq + tf + essay
    }
}
