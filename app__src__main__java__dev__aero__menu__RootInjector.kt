package dev.aero.menu

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * Copies the bundled injector binary and target .so out of assets, then runs the
 * injector as root against a running process. All of this is unverified against a
 * real device: read [InjectResult.log] after calling [inject] before trusting it.
 */
object RootInjector {

    data class InjectResult(val success: Boolean, val log: String)

    private fun copyAsset(context: Context, assetName: String, outFile: File) {
        context.assets.open(assetName).use { input ->
            FileOutputStream(outFile).use { output -> input.copyTo(output) }
        }
    }

    private fun runAsRoot(command: String): Pair<Int, String> {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val exit = process.waitFor()
        return exit to output
    }

    /**
     * @param packageName the target game's package, e.g. "com.axlebolt.standoff2"
     */
    fun inject(context: Context, packageName: String): InjectResult {
        val log = StringBuilder()
        try {
            val injectorFile = File(context.filesDir, "injector")
            val libFile = File(context.filesDir, "liblemmingrmt.so")
            copyAsset(context, "injector", injectorFile)
            copyAsset(context, "liblemmingrmt.so", libFile)

            val (pidExit, pidOut) = runAsRoot("pidof $packageName")
            val pid = pidOut.trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull()
            log.append("pidof $packageName -> exit=$pidExit out='${pidOut.trim()}'\n")
            if (pid == null) {
                log.append("Could not find a running process for $packageName. Start the game first.\n")
                return InjectResult(false, log.toString())
            }

            val chmodCmd = "chmod 755 ${injectorFile.absolutePath} ${libFile.absolutePath}"
            val (chmodExit, chmodOut) = runAsRoot(chmodCmd)
            log.append("chmod -> exit=$chmodExit out='$chmodOut'\n")
            if (chmodExit != 0) {
                return InjectResult(false, log.toString())
            }

            val injectCmd = "${injectorFile.absolutePath} $pid ${libFile.absolutePath}"
            val (injectExit, injectOut) = runAsRoot(injectCmd)
            log.append("inject -> exit=$injectExit\n$injectOut\n")
            return InjectResult(injectExit == 0, log.toString())
        } catch (e: Exception) {
            log.append("Exception: ${e.javaClass.simpleName}: ${e.message}\n")
            return InjectResult(false, log.toString())
        }
    }
}
