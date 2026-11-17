package app.darek.listen

import android.content.Context
import android.content.res.AssetManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.vosk.Model
import java.io.File

/**
 * Copies the recogniser's model out of the APK into `noBackupFilesDir` (ADR-0009). A directory with
 * the final name is complete, because it is only ever made by renaming a finished copy.
 */
object RecogniserModel {
    const val NAME = "vosk-model-small-pl-0.22"
    private const val PREFIX = "vosk-model-"

    fun unpack(context: Context): File {
        val root = context.noBackupFilesDir
        val model = File(root, NAME)
        if (model.isDirectory) return model
        root.listFiles()?.filter { it.name.startsWith(PREFIX) }?.forEach { it.deleteRecursively() }
        val partial = File(root, "$NAME.tmp")
        copy(context.assets, "vosk/$NAME", partial)
        check(partial.renameTo(model)) { "could not rename $partial to $model" }
        return model
    }

    private fun copy(
        assets: AssetManager,
        path: String,
        into: File,
    ) {
        val children = assets.list(path).orEmpty()
        if (children.isEmpty()) {
            into.parentFile?.mkdirs()
            assets.open(path).use { input -> into.outputStream().use { input.copyTo(it) } }
        } else {
            into.mkdirs()
            children.forEach { copy(assets, "$path/$it", File(into, it)) }
        }
    }
}

/** Loaded once per process, for Listening and for the install check's [Vocabulary] (ADR-0015). */
class LoadedModel(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val loading = Mutex()

    @Volatile private var model: Model? = null

    suspend fun get(): Model =
        model ?: loading.withLock { model ?: withContext(Dispatchers.IO) { load() }.also { model = it } }

    private fun load(): Model {
        val started = SystemClock.elapsedRealtime()
        val directory = RecogniserModel.unpack(appContext)
        val unpacked = SystemClock.elapsedRealtime()
        return Model(directory.path).also {
            Log.i(TAG, "unpack=${unpacked - started}ms load=${SystemClock.elapsedRealtime() - unpacked}ms")
        }
    }

    private companion object {
        const val TAG = "DarekModel"
    }
}
