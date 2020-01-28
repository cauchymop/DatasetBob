package com.cauchymop.datasetbob.utils

import android.content.Context
import com.cauchymop.datasetbob.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/** Use external media if it is available, our app's file directory otherwise */
fun getOutputDirectory(context: Context): File {
    val appContext = context.applicationContext
    val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
        File(it, appContext.resources.getString(R.string.app_name)).apply { mkdirs() } }
    return if (mediaDir != null && mediaDir.exists())
        mediaDir else appContext.filesDir
}

/** Helper function used to create a timestamped file */
fun createFile(baseFolder: File, format: String, extension: String) =
    File(baseFolder, SimpleDateFormat(format, Locale.US)
        .format(System.currentTimeMillis()) + extension)