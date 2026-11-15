package org.holidaylink.app.net

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer

/**
 * Wraps a request body and reports how many bytes have gone out.
 *
 * The chat shows this as a progress bar on the outgoing attachment, so a slow upload on
 * mobile data looks like WhatsApp's instead of a frozen screen.
 */
class ProgressRequestBody(
    private val delegate: RequestBody,
    private val onProgress: (Int) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = delegate.contentType()

    override fun contentLength(): Long = delegate.contentLength()

    override fun isOneShot(): Boolean = delegate.isOneShot()

    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        if (total <= 0L) {
            delegate.writeTo(sink)
            onProgress(100)
            return
        }
        var written = 0L
        var lastPercent = -1
        val counting = object : ForwardingSink(sink) {
            override fun write(source: Buffer, byteCount: Long) {
                super.write(source, byteCount)
                written += byteCount
                val percent = ((written * 100) / total).toInt().coerceIn(0, 100)
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(percent)
                }
            }
        }
        val buffered: BufferedSink = counting.buffer()
        delegate.writeTo(buffered)
        buffered.flush()
        onProgress(100)
    }
}

/** Small helper so callers can build a progress-reporting body without extra imports. */
fun RequestBody.withProgress(onProgress: (Int) -> Unit): RequestBody =
    ProgressRequestBody(this, onProgress)
