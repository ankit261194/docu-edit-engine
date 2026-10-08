package com.docu.editor.core.batch

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * High-speed Full-Text Search (FTS) query match item.
 */
data class FtsSearchResult(
    val docId: String,
    val filePath: String,
    val fileName: String,
    val pageNumber: Int,
    val snippet: String,
    val matchCount: Int,
    val timestamp: Long
)

/**
 * Enterprise SQLite FTS5 / FTS4 Full-Text Document Search Index Manager.
 * Enables 0ms offline full-text search across thousands of batch-processed document pages.
 */
class DocumentFtsIndexManager private constructor(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        private const val DATABASE_NAME = "docu_edit_fts.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_FTS = "doc_search_fts"

        @Volatile
        private var instance: DocumentFtsIndexManager? = null

        fun getInstance(context: Context): DocumentFtsIndexManager {
            return instance ?: synchronized(this) {
                instance ?: DocumentFtsIndexManager(context).also { instance = it }
            }
        }
    }

    private var isFts5Supported = true

    override fun onCreate(db: SQLiteDatabase) {
        try {
            // Attempt FTS5 virtual table
            db.execSQL(
                """
                CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE_FTS USING fts5(
                    doc_id UNINDEXED,
                    file_path UNINDEXED,
                    file_name,
                    extracted_text,
                    page_number UNINDEXED,
                    timestamp UNINDEXED
                )
                """.trimIndent()
            )
            isFts5Supported = true
        } catch (_: Exception) {
            // Fallback to FTS4 virtual table for maximum backward compatibility
            try {
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE_FTS USING fts4(
                        doc_id,
                        file_path,
                        file_name,
                        extracted_text,
                        page_number,
                        timestamp
                    )
                    """.trimIndent()
                )
                isFts5Supported = false
            } catch (_: Exception) {}
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FTS")
        onCreate(db)
    }

    /**
     * Indexes a single document page's extracted OCR text.
     */
    suspend fun indexPage(
        docId: String,
        filePath: String,
        fileName: String,
        extractedText: String,
        pageNumber: Int,
        timestamp: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        if (extractedText.isBlank()) return@withContext
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put("doc_id", docId)
                put("file_path", filePath)
                put("file_name", fileName)
                put("extracted_text", extractedText)
                put("page_number", pageNumber)
                put("timestamp", timestamp)
            }
            db.insert(TABLE_FTS, null, values)
        } catch (_: Exception) {}
    }

    /**
     * Performs instant sub-millisecond full-text search across all indexed OCR pages.
     */
    suspend fun search(query: String, maxResults: Int = 50): List<FtsSearchResult> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        val results = mutableListOf<FtsSearchResult>()
        try {
            val db = readableDatabase
            // Clean search query to prevent SQL syntax errors in FTS match expression
            val sanitized = trimmed.replace(Regex("[^a-zA-Z0-9\\u0900-\\u097F\\s]"), " ").trim()
            if (sanitized.isBlank()) return@withContext emptyList()

            // Construct FTS prefix query for auto-complete feel (e.g. "invoice*" or "tax*")
            val ftsQuery = sanitized.split("\\s+".toRegex()).joinToString(" ") { "$it*" }

            val cursor = db.rawQuery(
                "SELECT doc_id, file_path, file_name, extracted_text, page_number, timestamp " +
                "FROM $TABLE_FTS WHERE $TABLE_FTS MATCH ? LIMIT ?",
                arrayOf(ftsQuery, maxResults.toString())
            )

            cursor.use { c ->
                val docIdIdx = c.getColumnIndex("doc_id")
                val pathIdx = c.getColumnIndex("file_path")
                val nameIdx = c.getColumnIndex("file_name")
                val textIdx = c.getColumnIndex("extracted_text")
                val pageIdx = c.getColumnIndex("page_number")
                val timeIdx = c.getColumnIndex("timestamp")

                while (c.moveToNext()) {
                    val docId = if (docIdIdx != -1) c.getString(docIdIdx) else ""
                    val path = if (pathIdx != -1) c.getString(pathIdx) else ""
                    val name = if (nameIdx != -1) c.getString(nameIdx) else ""
                    val text = if (textIdx != -1) c.getString(textIdx) else ""
                    val pageNum = if (pageIdx != -1) c.getInt(pageIdx) else 1
                    val time = if (timeIdx != -1) c.getLong(timeIdx) else 0L

                    // Generate contextual match snippet
                    val snippet = createSnippet(text, sanitized)
                    val count = countOccurrences(text, sanitized)

                    results.add(
                        FtsSearchResult(
                            docId = docId,
                            filePath = path,
                            fileName = name,
                            pageNumber = pageNum,
                            snippet = snippet,
                            matchCount = max(1, count),
                            timestamp = time
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        results
    }

    private fun createSnippet(fullText: String, query: String, contextRadius: Int = 45): String {
        val firstWord = query.split("\\s+".toRegex()).firstOrNull() ?: query
        val matchIndex = fullText.indexOf(firstWord, ignoreCase = true)
        if (matchIndex == -1) {
            return if (fullText.length > 90) fullText.substring(0, 90) + "..." else fullText
        }

        val start = max(0, matchIndex - contextRadius)
        val end = min(fullText.length, matchIndex + firstWord.length + contextRadius)

        val prefix = if (start > 0) "... " else ""
        val suffix = if (end < fullText.length) " ..." else ""

        return prefix + fullText.substring(start, end).replace("\n", " ").trim() + suffix
    }

    private fun countOccurrences(text: String, query: String): Int {
        var count = 0
        var idx = 0
        val target = query.lowercase()
        val src = text.lowercase()
        while (idx != -1) {
            idx = src.indexOf(target, idx)
            if (idx != -1) {
                count++
                idx += target.length
            }
        }
        return count
    }

    suspend fun getIndexedCount(): Int = withContext(Dispatchers.IO) {
        return@withContext try {
            val db = readableDatabase
            db.rawQuery("SELECT COUNT(*) FROM $TABLE_FTS", null).use {
                if (it.moveToFirst()) it.getInt(0) else 0
            }
        } catch (_: Exception) {
            0
        }
    }

    suspend fun clearIndex() = withContext(Dispatchers.IO) {
        try {
            val db = writableDatabase
            db.delete(TABLE_FTS, null, null)
        } catch (_: Exception) {}
    }
}
