package com.example.engine

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.IOException

class CountingRequestBody(
    private val totalBytes: Long,
    private val onBytesWritten: (bytesWrittenInChunk: Long, totalWrittenSoFar: Long) -> Unit
) : RequestBody() {

    private val mediaType: MediaType? = "application/octet-stream".toMediaTypeOrNull()

    override fun contentType(): MediaType? = mediaType

    override fun contentLength(): Long = totalBytes

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        // Use 32KB chunks and flush to TCP socket after each chunk
        // This ensures progress is reported at the actual network transmission rate
        // rather than dumping all data in memory instantly
        val chunkSize = 32768
        val buffer = ByteArray(chunkSize) { (it % 256).toByte() }

        var uploaded = 0L
        while (uploaded < totalBytes) {
            val remaining = totalBytes - uploaded
            val toWrite = remaining.coerceAtMost(chunkSize.toLong()).toInt()
            sink.write(buffer, 0, toWrite)
            sink.flush()
            uploaded += toWrite
            onBytesWritten(toWrite.toLong(), uploaded)
        }
    }
}
