package com.teinzz.gamenuke

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class ShellResult(val exitCode: Int, val output: String, val success: Boolean)

/**
 * Shizuku API 13 keeps newProcess private during the transition to UserService.
 * We access that compatibility method reflectively so this source compiles against
 * API 13 while retaining the service's actual shell permission model.
 */
object ShellRunner {
    suspend fun execute(command: String): ShellResult = withContext(Dispatchers.IO) {
        if (!Shizuku.pingBinder()) return@withContext ShellResult(-1, "Shizuku tidak aktif.", false)
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return@withContext ShellResult(-1, "Izin Shizuku belum diberikan.", false)
        }
        try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            val output = listOf(stdout.trim(), stderr.trim()).filter { it.isNotBlank() }.joinToString("\\n")
            val actual = output.ifBlank { if (code == 0) "Perintah selesai tanpa output." else "Exit code $code" }
            val rejected = listOf("permission denied", "not permitted", "unknown command", "not found", "operation not permitted", "failed").any { actual.contains(it, true) }
            ShellResult(code, actual, code == 0 && !rejected)
        } catch (e: Exception) {
            ShellResult(-1, "Gagal: ${e.javaClass.simpleName}: ${e.message ?: "unknown error"}", false)
        }
    }
}
