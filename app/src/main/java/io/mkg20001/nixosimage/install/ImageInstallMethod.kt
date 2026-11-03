package io.mkg20001.nixosimage.install

import android.content.Context
import android.content.res.AssetManager
import io.mkg20001.nixosimage.data.GitHubReleaseAsset
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

interface ImageInstallMethod {
    val id: String
    val display: Int

    fun isAvailable(): Boolean
    // image is null when the method doesn't need it (needsImage)
    suspend fun installImage (
        context: Context,
        image: File?,
        asset: GitHubReleaseAsset,
        assets: AssetManager,
        progress: MutableStateFlow<Int>
    ): Boolean
    val needsImage: Boolean
        get() = true
    val needsCleanup: Boolean
        get() = false
    val needsExternalStorage: Boolean
        get() = true
    val needsLaunchTerminalAfterwards: Boolean
        get() = true
    val needsImageClean: Boolean
        get() = true
    val showOpenTerminalAgainBtn: Boolean
        get() = true

    fun doCleanup() {
        throw RuntimeException("not applicable")
    }
}

object InstallMethods {
    val methods = listOf(InPlaceInstallMethod, DebugInstallMethod, MagiskInstallMethod, ReplaceInstallMethod)
    fun availableMethods(): List<ImageInstallMethod> {
        // The Android 17 Terminal boots its own kernel and rejects other disk images,
        // so none of the image based methods work there
        if (InPlaceInstallMethod.isAvailable()) {
            return listOf(InPlaceInstallMethod)
        }
        return methods.filter { it.isAvailable() }
    }
    fun getMethod(id: String): ImageInstallMethod? {
        return methods.filter { it.id == id }.getOrNull(0)
    }
}