package com.mediaforge.studio.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Permissions {

    /** READ_MEDIA_IMAGES on 13+, READ_EXTERNAL_STORAGE on 10-12, read+write on 8-9. */
    fun required(): Array<String> = when {
        Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        Build.VERSION.SDK_INT >= 29 -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    fun granted(ctx: Context): Boolean = required().all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Saving needs a runtime permission only on API 26-28; scoped storage covers 29+. */
    fun writeBlocked(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 29 && !granted(ctx)
}
