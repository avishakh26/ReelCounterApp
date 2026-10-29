package com.reelcounter.core.data.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.reelcounter.core.common.dispatchers.Dispatcher
import com.reelcounter.core.common.dispatchers.RcDispatchers
import com.reelcounter.core.domain.Outcome
import com.reelcounter.core.domain.profile.AvatarImage
import com.reelcounter.core.domain.profile.AvatarImageProcessor
import com.reelcounter.core.domain.profile.ProfileError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

internal class AndroidAvatarImageProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(RcDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : AvatarImageProcessor {

    override suspend fun process(sourceUri: String): Outcome<AvatarImage, ProfileError> = withContext(ioDispatcher) {
        try {
            val decoded = decode(sourceUri.toUri())
            val square = centerSquare(decoded)
            val (format, mimeType) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY to "image/webp"
            } else {
                Bitmap.CompressFormat.JPEG to "image/jpeg"
            }
            val bytes = ByteArrayOutputStream().use { out ->
                check(square.compress(format, QUALITY, out)) { "Encoding failed" }
                out.toByteArray()
            }
            Outcome.Success(AvatarImage(bytes, mimeType))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Unreadable, unsupported or revoked URI: all mean "pick another image".
            Outcome.Failure(ProfileError.ImageUnreadable)
        }
    }

    /** Decodes downsampled so the short side is about [SIZE] px; huge photos never load in full. */
    private fun decode(uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder also applies EXIF rotation.
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val shortSide = min(info.size.width, info.size.height)
                if (shortSide > SIZE) {
                    val scale = SIZE.toFloat() / shortSide
                    decoder.setTargetSize(
                        max(1, (info.size.width * scale).toInt()),
                        max(1, (info.size.height * scale).toInt()),
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            // Android 8.x: BitmapFactory, which ignores EXIF rotation. These versions are a small
            // share of devices; accepted for now.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= SIZE) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("Undecodable image")
        }

    private fun centerSquare(source: Bitmap): Bitmap {
        val side = min(source.width, source.height)
        val cropped = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        return if (side == SIZE) cropped else cropped.scale(SIZE, SIZE)
    }

    private companion object {
        const val SIZE = 512
        const val QUALITY = 85
    }
}
