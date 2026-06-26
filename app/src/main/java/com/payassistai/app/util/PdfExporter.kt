package com.payassistai.app.util

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.payassistai.app.data.ChatMessage
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


object PdfExporter {

    fun exportChatAsPdf(context: Context, messages: List<ChatMessage>, merchantName: String) {
        if (messages.isEmpty()) {
            Toast.makeText(context, "No messages to export.", Toast.LENGTH_SHORT).show()
            return
        }

        val pdfDocument = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()

        val titlePaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 18f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }
        val textPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }
        val senderPaint = Paint().apply {
            color = android.graphics.Color.BLUE
            textSize = 12f
            isAntiAlias = true
            typeface = Typeface.DEFAULT_BOLD
        }

        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        var y = 30f
        val margin = 40f
        val lineHeight = 20f
        val maxWidth = pageWidth - 2 * margin

        canvas.drawText("PayAssistAI - Chat Export", margin, y, titlePaint)
        y += lineHeight + 5

        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date())
        canvas.drawText("Exported: $dateStr", margin, y, textPaint)
        y += lineHeight + 5

        canvas.drawText("Merchant: $merchantName", margin, y, textPaint)
        y += lineHeight + 10

        for (message in messages) {
            val sender = if (message.sender == "user") "User" else "Bot"
            val timestamp = dateFormat.format(Date(message.timestamp))
            val senderLine = "$sender ($timestamp):"
            canvas.drawText(senderLine, margin, y, senderPaint)
            y += lineHeight

            val lines = mutableListOf<String>()
            val words = message.text.split(" ")
            val sb = StringBuilder()
            for (word in words) {
                val test = if (sb.isEmpty()) word else "$sb $word"
                if (textPaint.measureText(test) <= maxWidth) {
                    sb.append(if (sb.isEmpty()) word else " $word")
                } else {
                    lines.add(sb.toString())
                    sb.clear()
                    sb.append(word)
                }
            }
            if (sb.isNotEmpty()) lines.add(sb.toString())

            var newY = y
            for (line in lines) {
                canvas.drawText(line, margin, newY, textPaint)
                newY += lineHeight
            }
            if (lines.isEmpty()) {
                canvas.drawText("(empty message)", margin, newY, textPaint)
                newY += lineHeight
            }

            newY += 8f
            y = newY

            if (y > pageHeight - 40) {
                pdfDocument.finishPage(page)
                val newPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pdfDocument.pages.size + 1).create()
                page = pdfDocument.startPage(newPageInfo)
                canvas = page.canvas
                y = 30f
            }
        }

        pdfDocument.finishPage(page)

        try {
            val fileName = "Chat_Export_${System.currentTimeMillis()}.pdf"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        pdfDocument.writeTo(outputStream)
                        pdfDocument.close()
                        Toast.makeText(context, "✅ PDF saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
                    }
                } ?: run {
                    Toast.makeText(context, "❌ Failed to save PDF", Toast.LENGTH_SHORT).show()
                }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                    pdfDocument.close()
                    Toast.makeText(context, "✅ PDF saved to Downloads: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Log.e("PdfExporter", "PDF export error", e)
            Toast.makeText(context, "❌ Error exporting PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            pdfDocument.close()
        }
    }
}