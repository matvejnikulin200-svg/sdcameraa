package com.example.selfdestructcam

import android.content.Context
import android.net.Uri
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object Deleter {
    private const val KEY = "pending"
    private fun prefs(c: Context) = c.getSharedPreferences("sd", Context.MODE_PRIVATE)

    fun schedule(c: Context, uri: Uri, delayMs: Long) {
        val at = System.currentTimeMillis() + delayMs
        val set = prefs(c).getStringSet(KEY, emptySet())!!.toMutableSet()
        set.add("$uri|$at")
        prefs(c).edit().putStringSet(KEY, set).apply()
        val req = OneTimeWorkRequestBuilder<DeleteWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS).build()
        WorkManager.getInstance(c).enqueue(req)
    }

    /** Удаляет все просроченные фото. Вызывается таймером и при запуске приложения. */
    fun sweep(c: Context) {
        val now = System.currentTimeMillis()
        val keep = mutableSetOf<String>()
        for (e in prefs(c).getStringSet(KEY, emptySet())!!) {
            val parts = e.split("|")
            if (parts[1].toLong() <= now) {
                try { c.contentResolver.delete(Uri.parse(parts[0]), null, null) } catch (_: Exception) {}
            } else keep.add(e)
        }
        prefs(c).edit().putStringSet(KEY, keep).apply()
    }
}

class DeleteWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        Deleter.sweep(applicationContext)
        return Result.success()
    }
}
