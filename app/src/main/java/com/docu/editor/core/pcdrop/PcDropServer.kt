package com.docu.editor.core.pcdrop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.docu.editor.core.history.DocumentHistoryManager
import com.docu.editor.core.history.SavedDocumentItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class PcDropServer(
    private val context: Context,
    private val port: Int = 8080
) {
    private val tag = "PcDropServer"
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val isRunningFlag = AtomicBoolean(false)
    val connectedClientsCount = AtomicInteger(0)

    // Security PIN & Session Token
    var serverPin: String = "%04d".format(Random.nextInt(1000, 10000))
        private set
    private var sessionToken: String = UUID.randomUUID().toString()

    var onLogMessage: ((String) -> Unit)? = null
    var onFileReceived: ((File) -> Unit)? = null

    fun isRunning(): Boolean = isRunningFlag.get()

    /**
     * Regenerates a fresh 4-digit security PIN and session token.
     */
    fun refreshPin(): String {
        serverPin = "%04d".format(Random.nextInt(1000, 10000))
        sessionToken = UUID.randomUUID().toString()
        return serverPin
    }

    /**
     * Starts the embedded HTTP server on background coroutine.
     */
    fun start(scope: CoroutineScope): Boolean {
        if (isRunningFlag.get()) return true

        return try {
            val sSocket = ServerSocket(port)
            serverSocket = sSocket
            isRunningFlag.set(true)
            log("PC Drop server started on port $port (PIN: $serverPin)")

            serverJob = scope.launch(Dispatchers.IO) {
                while (isActive && !sSocket.isClosed) {
                    try {
                        val clientSocket = sSocket.accept()
                        launch(Dispatchers.IO) {
                            handleClient(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!sSocket.isClosed) {
                            log("Connection accept error: ${e.message}")
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            log("Failed to start PC Drop server: ${e.message}")
            isRunningFlag.set(false)
            false
        }
    }

    /**
     * Stops the embedded HTTP server.
     */
    fun stop() {
        isRunningFlag.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
        log("PC Drop server stopped.")
    }

    private fun log(msg: String) {
        Log.i(tag, msg)
        onLogMessage?.invoke(msg)
    }

    private suspend fun handleClient(socket: Socket) {
        connectedClientsCount.incrementAndGet()
        try {
            socket.soTimeout = 15000
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())

            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0].uppercase()
            val uriWithQuery = parts[1]
            val path = uriWithQuery.substringBefore("?")
            val query = if (uriWithQuery.contains("?")) uriWithQuery.substringAfter("?") else ""
            val queryParams = parseQueryParams(query)

            // Read Headers
            val headers = mutableMapOf<String, String>()
            while (true) {
                val headerLine = readLine(input) ?: break
                if (headerLine.isEmpty()) break
                val colonIdx = headerLine.indexOf(':')
                if (colonIdx > 0) {
                    val key = headerLine.substring(0, colonIdx).trim().lowercase()
                    val value = headerLine.substring(colonIdx + 1).trim()
                    headers[key] = value
                }
            }

            val clientAuthToken = headers["authorization"] ?: queryParams["token"] ?: ""
            val isAuthorized = clientAuthToken.isNotBlank() && clientAuthToken == sessionToken

            // Route Requests
            when {
                method == "GET" && path == "/" -> {
                    val html = PcDropWebTemplate.getHtml()
                    sendResponse(output, 200, "OK", "text/html; charset=UTF-8", html.toByteArray())
                }

                method == "POST" && path == "/api/auth" -> {
                    handleAuthRequest(input, headers, output)
                }

                method == "GET" && path == "/api/documents" -> {
                    if (!isAuthorized) {
                        sendResponse(output, 401, "Unauthorized", "application/json", """{"error":"PIN required"}""".toByteArray())
                    } else {
                        handleGetDocuments(output)
                    }
                }

                method == "GET" && path == "/api/thumb" -> {
                    val docId = queryParams["id"] ?: ""
                    handleGetThumb(docId, output)
                }

                method == "GET" && path == "/api/download" -> {
                    val docId = queryParams["id"] ?: ""
                    handleDownloadDocument(docId, output)
                }

                method == "GET" && path == "/api/download-all-zip" -> {
                    handleDownloadAllZip(output)
                }

                method == "POST" && path == "/api/upload" -> {
                    if (!isAuthorized) {
                        sendResponse(output, 401, "Unauthorized", "application/json", """{"error":"PIN required"}""".toByteArray())
                    } else {
                        handleUpload(input, headers, output)
                    }
                }

                method == "GET" && path == "/api/status" -> {
                    val json = JSONObject().apply {
                        put("running", isRunningFlag.get())
                        put("clients", connectedClientsCount.get())
                        put("port", port)
                    }
                    sendResponse(output, 200, "OK", "application/json", json.toString().toByteArray())
                }

                else -> {
                    sendResponse(output, 404, "Not Found", "text/plain", "Endpoint not found".toByteArray())
                }
            }
        } catch (_: Exception) {
            // Socket closed / timeout
        } finally {
            connectedClientsCount.decrementAndGet()
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun handleAuthRequest(input: InputStream, headers: Map<String, String>, output: OutputStream) {
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
        val bodyBytes = ByteArray(contentLength)
        var totalRead = 0
        while (totalRead < contentLength) {
            val r = input.read(bodyBytes, totalRead, contentLength - totalRead)
            if (r == -1) break
            totalRead += r
        }
        val bodyStr = String(bodyBytes, Charsets.UTF_8)
        val pin = try {
            JSONObject(bodyStr).optString("pin", "")
        } catch (_: Exception) { "" }

        if (pin.trim() == serverPin.trim()) {
            val res = JSONObject().apply {
                put("success", true)
                put("token", sessionToken)
            }
            log("Laptop successfully authenticated with PIN $pin")
            sendResponse(output, 200, "OK", "application/json", res.toString().toByteArray())
        } else {
            val res = JSONObject().apply {
                put("success", false)
                put("error", "Invalid PIN")
            }
            log("Authentication rejected: wrong PIN ($pin)")
            sendResponse(output, 401, "Unauthorized", "application/json", res.toString().toByteArray())
        }
    }

    private suspend fun handleGetDocuments(output: OutputStream) {
        val docs = DocumentHistoryManager.getSavedDocuments(context)
        val array = JSONArray()
        for (doc in docs) {
            val isPdf = doc.filePath.endsWith(".pdf", ignoreCase = true)
            val obj = JSONObject().apply {
                put("id", doc.id)
                put("title", doc.title)
                put("formattedDate", doc.formattedDate)
                put("formattedSize", doc.formattedSize)
                put("pageCount", doc.pageCount)
                put("isPdf", isPdf)
            }
            array.put(obj)
        }
        sendResponse(output, 200, "OK", "application/json", array.toString().toByteArray())
    }

    private suspend fun handleGetThumb(docId: String, output: OutputStream) {
        val docs = DocumentHistoryManager.getSavedDocuments(context)
        val doc = docs.find { it.id == docId }
        if (doc != null && doc.thumbnailPath.isNotBlank() && File(doc.thumbnailPath).exists()) {
            val thumbFile = File(doc.thumbnailPath)
            sendFileResponse(output, thumbFile, "image/jpeg", false)
        } else {
            sendResponse(output, 404, "Not Found", "text/plain", "Thumbnail not found".toByteArray())
        }
    }

    private suspend fun handleDownloadDocument(docId: String, output: OutputStream) {
        val docs = DocumentHistoryManager.getSavedDocuments(context)
        val doc = docs.find { it.id == docId }
        if (doc != null && File(doc.filePath).exists()) {
            val file = File(doc.filePath)
            val isPdf = file.name.endsWith(".pdf", ignoreCase = true)
            val mimeType = if (isPdf) "application/pdf" else "image/jpeg"
            val downloadName = if (doc.title.contains(".")) doc.title else "${doc.title}.${if (isPdf) "pdf" else "jpg"}"
            log("Sending download: $downloadName (${file.length() / 1024} KB)")
            sendFileResponse(output, file, mimeType, true, downloadName)
        } else {
            sendResponse(output, 404, "Not Found", "text/plain", "File not found".toByteArray())
        }
    }

    private suspend fun handleDownloadAllZip(output: OutputStream) = withContext(Dispatchers.IO) {
        val docs = DocumentHistoryManager.getSavedDocuments(context)
        val zipFile = File(context.cacheDir, "DocuEdit_Scans_${System.currentTimeMillis()}.zip")

        try {
            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                val usedNames = mutableSetOf<String>()
                for (doc in docs) {
                    val f = File(doc.filePath)
                    if (f.exists()) {
                        var entryName = f.name
                        var counter = 1
                        while (usedNames.contains(entryName)) {
                            entryName = "${f.nameWithoutExtension}_$counter.${f.extension}"
                            counter++
                        }
                        usedNames.add(entryName)

                        zos.putNextEntry(ZipEntry(entryName))
                        FileInputStream(f).use { fis ->
                            fis.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                }
            }

            log("Streaming all-documents ZIP (${zipFile.length() / 1024} KB) to laptop")
            sendFileResponse(output, zipFile, "application/zip", true, "DocuEdit_All_Scans.zip")
        } finally {
            zipFile.delete()
        }
    }

    private suspend fun handleUpload(input: InputStream, headers: Map<String, String>, output: OutputStream) = withContext(Dispatchers.IO) {
        val contentType = headers["content-type"] ?: ""
        val contentLength = headers["content-length"]?.toIntOrNull() ?: 0

        if (!contentType.contains("multipart/form-data")) {
            sendResponse(output, 400, "Bad Request", "application/json", """{"error":"Multipart required"}""".toByteArray())
            return@withContext
        }

        val boundary = contentType.substringAfter("boundary=", "").trim()
        if (boundary.isBlank() || contentLength <= 0) {
            sendResponse(output, 400, "Bad Request", "application/json", """{"error":"Invalid boundary"}""".toByteArray())
            return@withContext
        }

        // Parse Multipart Body
        val receivedFile = parseMultipartFile(input, boundary, contentLength)
        if (receivedFile != null && receivedFile.exists() && receivedFile.length() > 0L) {
            val title = receivedFile.nameWithoutExtension
            log("Received file from laptop: ${receivedFile.name} (${receivedFile.length() / 1024} KB)")

            // Generate thumbnail & register in DocumentHistoryManager
            val thumbBmp = try {
                if (receivedFile.name.endsWith(".pdf", ignoreCase = true)) {
                    com.docu.editor.core.pdf.PdfPageLoader.renderPageToBitmap(context, android.net.Uri.fromFile(receivedFile), 0)
                } else {
                    BitmapFactory.decodeFile(receivedFile.absolutePath)
                }
            } catch (_: Exception) { null }

            DocumentHistoryManager.saveExistingDocumentFile(
                context = context,
                title = title,
                filePath = receivedFile.absolutePath,
                thumbnailBitmap = thumbBmp,
                category = "Transferred from PC"
            )

            thumbBmp?.recycle()
            onFileReceived?.invoke(receivedFile)

            sendResponse(output, 200, "OK", "application/json", """{"success":true,"filename":"${receivedFile.name}"}""".toByteArray())
        } else {
            sendResponse(output, 500, "Error", "application/json", """{"error":"Failed to save uploaded file"}""".toByteArray())
        }
    }

    private fun parseMultipartFile(input: InputStream, boundary: String, totalBytes: Int): File? {
        val tempDir = File(context.cacheDir, "pcdrop_uploads").apply { mkdirs() }
        var uploadedFile: File? = null

        val boundaryMarker = "--$boundary".toByteArray(Charsets.US_ASCII)
        val stream = BufferedInputStream(input)

        // Read all multipart bytes
        val buffer = ByteArray(totalBytes)
        var readSoFar = 0
        while (readSoFar < totalBytes) {
            val r = stream.read(buffer, readSoFar, totalBytes - readSoFar)
            if (r == -1) break
            readSoFar += r
        }

        val bodyString = String(buffer, Charsets.ISO_8859_1)
        val filenameMatch = Regex("filename=\"([^\"]+)\"").find(bodyString)
        val rawFilename = filenameMatch?.groupValues?.get(1) ?: "uploaded_doc_${System.currentTimeMillis()}.pdf"
        val cleanFilename = File(rawFilename).name.ifBlank { "file_${System.currentTimeMillis()}" }

        // Find binary payload after \r\n\r\n
        val headerEndIdx = bodyString.indexOf("\r\n\r\n")
        if (headerEndIdx != -1) {
            val payloadStart = headerEndIdx + 4
            // Find boundary end
            val payloadEnd = bodyString.lastIndexOf("\r\n--$boundary")
            if (payloadEnd > payloadStart) {
                uploadedFile = File(tempDir, cleanFilename)
                FileOutputStream(uploadedFile).use { fos ->
                    val payloadBytes = buffer.copyOfRange(payloadStart, payloadEnd)
                    fos.write(payloadBytes)
                }
            }
        }

        return uploadedFile
    }

    private fun sendResponse(
        output: OutputStream,
        statusCode: Int,
        statusText: String,
        contentType: String,
        body: ByteArray
    ) {
        val header = "HTTP/1.1 $statusCode $statusText\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))
        output.write(body)
        output.flush()
    }

    private fun sendFileResponse(
        output: OutputStream,
        file: File,
        contentType: String,
        isAttachment: Boolean,
        downloadName: String = file.name
    ) {
        val disp = if (isAttachment) "Content-Disposition: attachment; filename=\"$downloadName\"\r\n" else ""
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${file.length()}\r\n" +
                disp +
                "Connection: close\r\n" +
                "Access-Control-Allow-Origin: *\r\n\r\n"
        output.write(header.toByteArray(Charsets.UTF_8))

        FileInputStream(file).use { fis ->
            val buf = ByteArray(32768)
            var bytesRead: Int
            while (fis.read(buf).also { bytesRead = it } != -1) {
                output.write(buf, 0, bytesRead)
            }
        }
        output.flush()
    }

    private fun readLine(input: InputStream): String? {
        val sb = StringBuilder()
        var c = input.read()
        if (c == -1) return null
        while (c != -1 && c != '\n'.code) {
            if (c != '\r'.code) sb.append(c.toChar())
            c = input.read()
        }
        return sb.toString()
    }

    private fun parseQueryParams(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        for (param in query.split("&")) {
            val parts = param.split("=")
            if (parts.size == 2) {
                val key = try { URLDecoder.decode(parts[0], "UTF-8") } catch (_: Exception) { parts[0] }
                val value = try { URLDecoder.decode(parts[1], "UTF-8") } catch (_: Exception) { parts[1] }
                map[key] = value
            }
        }
        return map
    }
}
