package org.holidaylink.app.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * A crop window, normalized to the 0..1 size of the (already rotated) source image.
 * This is what the user framed inside the fixed crop guide in the editor — a real crop,
 * not a center-crop derived from a zoom factor.
 */
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    fun isIdentity(): Boolean = left <= 0.0001f && top <= 0.0001f && right >= 0.9999f && bottom >= 0.9999f
}

/**
 * A text or sticker placed on the photo, positioned the way WhatsApp's editor does it:
 * dragged and pinch/handle-resized by the user rather than pinned to a fixed spot.
 *
 * [x]/[y] are the overlay's center, normalized 0..1 against the final (cropped) image.
 * [scale] is relative to the overlay's natural on-canvas size (1f = as placed).
 */
data class PlacedOverlay(
    val id: String,
    val kind: String, // "text" | "sticker"
    val text: String = "",
    val colorArgb: Int = android.graphics.Color.WHITE,
    val stickerRef: String? = null,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f
)

/**
 * Small editing helpers used by the "edit before sending" screen.
 *
 * Everything here works on a copy of the file the user picked, never on the original:
 * the result is written to the app cache so the edit never touches the phone's gallery.
 */
object MediaEditor {

    /** Rotates a JPEG by 90° steps and returns the new bytes (null when it cannot). */
    fun rotateJpeg(bytes: ByteArray, degrees: Int): ByteArray? {
        if (degrees % 360 == 0) return bytes
        return try {
            val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            if (rotated !== source) source.recycle()
            val out = ByteArrayOutputStream()
            rotated.compress(Bitmap.CompressFormat.JPEG, MediaPicker.jpegQuality, out)
            rotated.recycle()
            out.toByteArray()
        } catch (_: Throwable) {
            null
        }
    }

    /** Decodes a downscaled preview bitmap for a local file. */
    fun preview(file: File, maxDimension: Int = 1080): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxDimension ||
                bounds.outHeight / (sample * 2) >= maxDimension
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(file.absolutePath, options)
        } else {
            null
        }
    } catch (_: Throwable) {
        null
    }

    /**
     * Scales an image down and re-encodes it as JPEG so it uploads fast on mobile data.
     * Same treatment the photo path has always used, now on a local file.
     */
    fun jpegBytes(file: File, maxDimension: Int = 1280, quality: Int = 78): ByteArray? {
        val bitmap = preview(file, maxDimension) ?: return null
        return try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        } catch (_: Throwable) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Rotates, crops to a real user-framed [cropRect] (not a center-crop derived from a zoom
     * factor), then bakes freehand marks and any placed text overlays onto a new JPEG copy.
     *
     * [cropRect] is normalized against the rotated source image. Sticker overlays are not
     * baked in here — they stay animated (Lottie) and are drawn live by the viewer at their
     * stored [PlacedOverlay] position, exactly like the editor preview.
     */
    fun applyPhotoEdits(
        file: File,
        degrees: Int,
        cropRect: CropRect,
        overlays: List<PlacedOverlay>,
        strokes: List<List<Pair<Float, Float>>>
    ): File? {
        val source = preview(file, 2048) ?: return null
        return try {
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
            if (rotated !== source) source.recycle()

            val left = (cropRect.left.coerceIn(0f, 1f) * rotated.width).toInt()
            val top = (cropRect.top.coerceIn(0f, 1f) * rotated.height).toInt()
            val right = (cropRect.right.coerceIn(0f, 1f) * rotated.width).toInt()
            val bottom = (cropRect.bottom.coerceIn(0f, 1f) * rotated.height).toInt()
            val cropW = (right - left).coerceAtLeast(1).coerceAtMost(rotated.width - left)
            val cropH = (bottom - top).coerceAtLeast(1).coerceAtMost(rotated.height - top)
            val cropped = if (cropRect.isIdentity()) {
                rotated
            } else {
                Bitmap.createBitmap(rotated, left.coerceIn(0, rotated.width - 1), top.coerceIn(0, rotated.height - 1), cropW, cropH)
            }
            if (cropped !== rotated) rotated.recycle()

            val output = cropped.copy(Bitmap.Config.ARGB_8888, true)
            if (cropped !== output) cropped.recycle()
            val canvas = android.graphics.Canvas(output)

            val linePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE; style = android.graphics.Paint.Style.STROKE
                strokeWidth = (output.width * 0.008f).coerceAtLeast(4f)
                strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
                setShadowLayer(5f, 1f, 2f, android.graphics.Color.BLACK)
            }
            strokes.forEach { points ->
                if (points.size >= 2) {
                    val path = android.graphics.Path()
                    path.moveTo(points.first().first.coerceIn(0f,1f)*output.width, points.first().second.coerceIn(0f,1f)*output.height)
                    points.drop(1).forEach { path.lineTo(it.first.coerceIn(0f,1f)*output.width, it.second.coerceIn(0f,1f)*output.height) }
                    canvas.drawPath(path, linePaint)
                }
            }

            // Text overlays are placed exactly where the user dragged/resized them in the
            // editor, not pinned to a fixed bottom-center spot.
            overlays.filter { it.kind == "text" && it.text.isNotBlank() }.forEach { overlay ->
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = overlay.colorArgb
                    textAlign = android.graphics.Paint.Align.CENTER
                    textSize = ((output.width * 0.075f) * overlay.scale).coerceIn(14f, output.width * 0.4f)
                    typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
                    setShadowLayer(7f, 2f, 3f, android.graphics.Color.BLACK)
                }
                val lines = overlay.text.take(120).split("\n").take(3)
                val lineHeight = paint.textSize * 1.15f
                val centerX = overlay.x.coerceIn(0f, 1f) * output.width
                val centerY = overlay.y.coerceIn(0f, 1f) * output.height
                val baseY = centerY - (lines.size - 1) * lineHeight / 2f
                canvas.save()
                canvas.rotate(overlay.rotationDeg, centerX, centerY)
                lines.forEachIndexed { i, line -> canvas.drawText(line, centerX, baseY + i * lineHeight, paint) }
                canvas.restore()
            }

            val out = File(file.parentFile, "edited-${System.currentTimeMillis()}.jpg")
            out.outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            output.recycle()
            out
        } catch (_: Throwable) { null }
    }

    /** Crop an avatar to a square, with user-controlled zoom and horizontal/vertical positioning. */
    fun cropSquareJpeg(bytes: ByteArray, zoom: Float, offsetX: Float, offsetY: Float): ByteArray? {
        return try {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val side = (minOf(source.width, source.height) / zoom.coerceIn(1f, 2.5f)).toInt().coerceAtLeast(1)
        val maxLeft = (source.width - side).coerceAtLeast(0)
        val maxTop = (source.height - side).coerceAtLeast(0)
        val left = (maxLeft / 2f + offsetX.coerceIn(-1f, 1f) * maxLeft / 2f).toInt().coerceIn(0, maxLeft)
        val top = (maxTop / 2f + offsetY.coerceIn(-1f, 1f) * maxTop / 2f).toInt().coerceIn(0, maxTop)
        val crop = Bitmap.createBitmap(source, left, top, side, side)
        if (crop !== source) source.recycle()
        val out = ByteArrayOutputStream()
        crop.compress(Bitmap.CompressFormat.JPEG, 92, out)
        crop.recycle()
        out.toByteArray()
        } catch (_: Throwable) { null }
    }

    /** Duration of a video in milliseconds, or 0 when it cannot be read. */
    fun videoDurationMs(file: File): Long = try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(file.absolutePath)
        val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
        retriever.release()
        raw?.toLongOrNull() ?: 0L
    } catch (_: Throwable) {
        0L
    }

    /** A poster frame for the video, taken at [atMs]. */
    fun videoThumbnail(file: File, atMs: Long = 0L): Bitmap? = try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(file.absolutePath)
        val frame = retriever.getFrameAtTime(
            (atMs * 1000).coerceAtLeast(0L),
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
        )
        retriever.release()
        frame
    } catch (_: Throwable) {
        null
    }

    /**
     * Cuts a video down to the chosen range without re-encoding it: the samples are copied
     * into a new MP4, which keeps the original quality and is fast on a phone.
     * Returns null when the format cannot be handled, so the caller can fall back to the
     * untouched file.
     */
    fun trimVideo(file: File, startMs: Long, endMs: Long, out: File): File? {
        if (endMs <= startMs) return null
        return try {
            if (videoDurationMs(file) <= 0L) return null
            trimWithMuxer(file, startMs, endMs, out)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Stream-copies the samples between [startMs] and [endMs] into [out].
     * Uses MediaExtractor + MediaMuxer, so no re-encode happens.
     */
    private fun trimWithMuxer(file: File, startMs: Long, endMs: Long, out: File): File? {
        return try {
            val extractor = android.media.MediaExtractor()
            // Read the file by path: the extractor needs the data to stay readable for as long
            // as it copies samples, which a closed stream would not allow.
            extractor.setDataSource(file.absolutePath)
            val muxer = android.media.MediaMuxer(
                out.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            val trackMap = HashMap<Int, Int>()
            var maxInputSize = 1L shl 20
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/") && !mime.startsWith("audio/")) continue
                extractor.selectTrack(i)
                trackMap[i] = muxer.addTrack(format)
                if (format.containsKey(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    maxInputSize = maxOf(
                        maxInputSize,
                        format.getLong(android.media.MediaFormat.KEY_MAX_INPUT_SIZE)
                    )
                }
            }
            if (trackMap.isEmpty()) {
                extractor.release()
                muxer.release()
                return null
            }
            muxer.start()
            val buffer = java.nio.ByteBuffer.allocateDirect(maxInputSize.toInt())
            val info = android.media.MediaCodec.BufferInfo()
            val startUs = startMs * 1000
            val endUs = endMs * 1000
            extractor.seekTo(startUs, android.media.MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            while (true) {
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val time = extractor.sampleTime
                if (time < startUs) {
                    extractor.advance()
                    continue
                }
                if (time > endUs) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = (time - startUs).coerceAtLeast(0)
                info.flags = extractor.sampleFlags
                val outTrack = trackMap[extractor.sampleTrackIndex]
                if (outTrack != null) muxer.writeSampleData(outTrack, buffer, info)
                extractor.advance()
            }
            muxer.stop()
            muxer.release()
            extractor.release()
            if (out.length() > 0L) out else null
        } catch (_: Throwable) {
            runCatching { out.delete() }
            null
        }
    }
}
