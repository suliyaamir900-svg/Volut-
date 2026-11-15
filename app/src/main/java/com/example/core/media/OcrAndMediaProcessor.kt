package com.example.core.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.core.crypto.VaultCryptoManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ImportedFileResult(
    val localPath: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val extractedText: String,
    val sha256: String
)

@Singleton
class OcrAndMediaProcessor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cryptoManager: VaultCryptoManager
) {
    private val textRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeTextFromImageUri(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val image = InputImage.fromFilePath(context, uri)
            val result = textRecognizer.process(image).await()
            result.text.trim()
        }
    }

    suspend fun recognizeTextFromFile(file: File): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            val result = textRecognizer.process(image).await()
            result.text.trim()
        }
    }

    suspend fun importUriToPrivateStorage(uri: Uri): Result<ImportedFileResult> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            val displayName = queryDisplayName(uri) ?: "imported_${System.currentTimeMillis()}"
            val destDir = File(context.filesDir, "vault_files").apply { mkdirs() }
            val safeName = "${UUID.randomUUID().toString().take(8)}_${displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
            val destFile = File(destDir, safeName)

            resolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalStateException("Unable to read selected file.")

            val extractedText = when {
                mime.startsWith("image/") -> recognizeTextFromFile(destFile).getOrDefault("")
                mime == "text/plain" || displayName.endsWith(".txt", ignoreCase = true) ->
                    destFile.readText().take(25000)
                else -> ""
            }

            val sha = cryptoManager.sha256Hex("${destFile.name}:${destFile.length()}")
            ImportedFileResult(
                localPath = destFile.absolutePath,
                fileName = displayName,
                mimeType = mime,
                sizeBytes = destFile.length(),
                extractedText = extractedText,
                sha256 = sha
            )
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        }
    }
}
