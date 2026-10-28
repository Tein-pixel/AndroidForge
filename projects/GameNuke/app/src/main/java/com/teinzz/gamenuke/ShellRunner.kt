package com.teinzz.gamenuke

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class ShellResult(val exitCode: Int, val output: String, val success: Boolean)

object ShellRunner {
    suspend fun execute(command: String): ShellResult = withContext(Dispatchers.IO) {
        if (!Shizuku.pingBinder()) return@withContext ShellResult(-1, "Shizuku tidak aktif.", false)
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return@withContext ShellResult(-1, "Izin Shizuku belum diberikan.", false)
        }
        try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            val rejected = listOf("permission denied", "not permitted", "unknown command", "not found", "operation not permitted", "failed").any { output.contains(it, true) }
            ShellResult(code, output.trim().ifBlank { if (code == 0) "Perintah selesai tanpa output." else "Exit code $code" }, code == 0 && !rejected)
        } catch (e: Exception) {
            ShellResult(-1, "Gagal: ${e.javaClass.simpleName}: ${e.message ?: "unknown error"}", false)
        }
    }
}
