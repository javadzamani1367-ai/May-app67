package ir.roozban.feature.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ir.roozban.core.designsystem.theme.Backgrounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Copies a picture the user picked into the app's private storage, scaled down. */
interface BackgroundImageStore {
    /** Returns false when the picture could not be read. */
    suspend fun save(uri: Uri): Boolean

    suspend fun clear()
}

class AndroidBackgroundImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackgroundImageStore {
    override suspend fun save(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / sample > MAX_SIDE || bounds.outHeight / sample > MAX_SIDE) sample *= 2
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching false
            val file = Backgrounds.imageFile(context.filesDir)
            file.parentFile?.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            bitmap.recycle()
            true
        }.getOrDefault(false)
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { Backgrounds.imageFile(context.filesDir).delete() }
    }

    private companion object {
        const val MAX_SIDE = 2048
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    abstract fun backgroundImages(impl: AndroidBackgroundImageStore): BackgroundImageStore
}
