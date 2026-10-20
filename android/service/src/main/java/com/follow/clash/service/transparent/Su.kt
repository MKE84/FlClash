package com.follow.clash.service.transparent

import com.follow.clash.common.GlobalState

private const val LOG_TAG = "TransparentProxy"

// One `su -c` per command: a `su` handed a whole script answers only the first
// command when its prompt grants it once.
internal object Su {
    private const val TIMEOUT_MILLIS = 10_000L

    class Failure(message: String) : IllegalStateException(message)

    fun isAvailable(): Boolean = runCatching { exec("true") }.isSuccess

    fun exec(statement: String): String {
        val process = try {
            ProcessBuilder("su", "-c", statement).redirectErrorStream(true).start()
        } catch (error: Exception) {
            throw Failure("su is not available: ${error.message}")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(TIMEOUT_MILLIS, java.util.concurrent.TimeUnit.MILLISECONDS)) {
            process.destroy()
            throw Failure("su timed out on: $statement")
        }
        if (process.exitValue() != 0) {
            throw Failure("su exited with ${process.exitValue()} on: $statement\n$output")
        }
        return output
    }

    fun execAll(statements: List<String>, ignoreFailure: Boolean = false) {
        for (statement in statements) {
            try {
                exec(statement)
            } catch (error: Failure) {
                if (!ignoreFailure) {
                    throw error
                }
                GlobalState.log("$LOG_TAG: ${error.message}")
            }
        }
    }
}
