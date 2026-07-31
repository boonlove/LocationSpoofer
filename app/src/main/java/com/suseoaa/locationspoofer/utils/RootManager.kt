package com.suseoaa.locationspoofer.utils

import com.suseoaa.locationspoofer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class RootManager {

    suspend fun checkRootAccess(): Boolean = withContext(Dispatchers.IO) {
        executeCommand("id").contains("uid=0(root)") || checkRootAccessInteractive()
    }

    // 备用方案：启动交互式 su shell，写入 stdin 并读取 stdout
    private suspend fun checkRootAccessInteractive(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = ProcessBuilder("su")
                .redirectErrorStream(true)
                .start()
            process.outputStream.bufferedWriter().use { writer ->
                writer.write("id\nexit\n")
                writer.flush()
            }
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.contains("uid=0(root)")
        } catch (e: Exception) {
            false
        }
    }

    suspend fun grantMockLocation(): Boolean = withContext(Dispatchers.IO) {
        val result =
            executeCommand("appops set ${BuildConfig.APPLICATION_ID} android:mock_location allow")
        result != "ERROR"
    }

    suspend fun revokeMockLocation(): Boolean = withContext(Dispatchers.IO) {
        val result =
            executeCommand("appops set ${BuildConfig.APPLICATION_ID} android:mock_location deny")
        result != "ERROR"
    }

    fun executeCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output.ifEmpty { "SUCCESS" }
        } catch (e: Exception) {
            "ERROR"
        }
    }

    fun executeCommandWithInput(command: String, input: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            process.outputStream.bufferedWriter().use { writer ->
                writer.write(input)
                writer.flush()
            }
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output.ifEmpty { "SUCCESS" }
        } catch (e: Exception) {
            "ERROR"
        }
    }
}
