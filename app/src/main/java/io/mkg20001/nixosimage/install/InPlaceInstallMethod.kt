package io.mkg20001.nixosimage.install

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import io.mkg20001.nixosimage.R
import io.mkg20001.nixosimage.data.GitHubReleaseAsset
import io.mkg20001.nixosimage.data.mkdirp
import io.sentry.Sentry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URL

/**
 * Android 17 Terminal: it boots its own Debian kernel, can't be written to from the VM any more
 * and shows "Upgrade to newer terminal" for any disk image it didn't install itself. So no image
 * is downloaded; NixOS is installed in place of Debian from inside the Terminal by nixos-avf's
 * initial/install-in-place.sh. The app puts the script into Downloads, copies the command that
 * runs it and opens the Terminal: apps can't run commands in the Terminal themselves.
 */
object InPlaceInstallMethod : ImageInstallMethod {
    val TAG = "InPlaceInstall"

    override val id = "inplace"

    override val display = R.string.method_inplace

    override val needsImage: Boolean = false
    override val needsImageClean: Boolean = false

    // Android 17 is API level 37
    override fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= 37

    private const val SCRIPT_URL =
        "https://raw.githubusercontent.com/slopmaxxer/nixos-avf/android-17/initial/install-in-place.sh"
    private const val DIR = "Download/nixos-avf"

    // The Terminal mounts the user's shared storage at /mnt/shared
    const val COMMAND = "bash /mnt/shared/$DIR/install.sh"

    fun channelFor(asset: GitHubReleaseAsset): String =
        "https://nixos.org/channels/nixos-${asset.version}"

    override suspend fun installImage(
        context: Context,
        image: File?,
        asset: GitHubReleaseAsset,
        assets: AssetManager,
        progress: MutableStateFlow<Int>
    ): Boolean {
        val dir = Environment.getExternalStoragePublicDirectory(DIR)
        if (!mkdirp(dir.path)) {
            return false
        }

        try {
            withContext(Dispatchers.IO) {
                Log.i(TAG, "Downloading $SCRIPT_URL")
                val script = URL(SCRIPT_URL).openStream().use { it.readBytes() }
                File(dir, "install-in-place.sh").writeBytes(script)
                progress.tryEmit(50)

                File(dir, "install.sh").writeText(
                    """
                    |#!/bin/bash
                    |# Written by the NixOS AVF image app: installs NixOS ${asset.version} in place of the
                    |# Terminal's Debian. Output is also logged to install.log next to this script.
                    |dir=${'$'}(dirname "${'$'}(readlink -f "${'$'}0")")
                    |# root gets "Operation not supported" opening files on the shared folder, so run a copy
                    |cp "${'$'}dir/install-in-place.sh" ~/install-in-place.sh
                    |sudo NIXOS_CHANNEL=${channelFor(asset)} bash ~/install-in-place.sh 2>&1 | tee "${'$'}dir/install.log"
                    |""".trimMargin()
                )
                progress.tryEmit(100)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Sentry.captureException(e)
            return false
        }

        withContext(Dispatchers.Main) {
            copyCommand(context)
        }

        return true
    }

    fun copyCommand(context: Context) {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("NixOS install command", COMMAND))
        Toast.makeText(context, R.string.toast_inplace_command, Toast.LENGTH_LONG).show()
    }
}
