// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.voice

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class VoiceInputException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A prepared HTTP request; building is kept separate from sending so it can be tested without network. */
class VoiceRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: ByteArray?,
    val method: String = if (body == null) "GET" else "POST",
)

/** Sends [request] and returns the parsed JSON response. Throws [VoiceInputException] on any failure. */
fun VoiceRequest.execute(timeoutMillis: Int = 60_000): JSONObject {
    val connection = try {
        URL(url).openConnection() as HttpURLConnection
    } catch (e: Exception) {
        throw VoiceInputException("Invalid URL: $url", e)
    }
    try {
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = timeoutMillis
        headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        if (body != null) {
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
        if (code !in 200..299)
            throw VoiceInputException("HTTP $code: ${errorMessage(text)}")
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            throw VoiceInputException("Unexpected response: ${text.take(200)}", e)
        }
    } catch (e: IOException) {
        throw VoiceInputException(e.message ?: e.javaClass.simpleName, e)
    } finally {
        connection.disconnect()
    }
}

/** Pulls the human-readable message out of the error bodies the supported services return. */
internal fun errorMessage(body: String): String {
    val json = try { JSONObject(body) } catch (_: Exception) { return body.take(200) }
    val error = json.opt("error")
    return when {
        error is JSONObject -> error.optString("message", error.toString())
        error is String -> error
        json.has("err_msg") -> json.getString("err_msg") // Deepgram
        json.has("message") -> json.getString("message")
        else -> body.take(200)
    }
}

/** Builds a multipart/form-data body; returns the content type header value and the body. */
internal fun multipart(fields: Map<String, String>, fileField: String, fileName: String, fileType: String, file: ByteArray): Pair<String, ByteArray> {
    val boundary = "KeliBoard" + System.nanoTime()
    val out = ByteArrayOutputStream()
    fun line(s: String) = out.write((s + "\r\n").toByteArray())
    fields.forEach { (name, value) ->
        line("--$boundary")
        line("Content-Disposition: form-data; name=\"$name\"")
        line("")
        line(value)
    }
    line("--$boundary")
    line("Content-Disposition: form-data; name=\"$fileField\"; filename=\"$fileName\"")
    line("Content-Type: $fileType")
    line("")
    out.write(file)
    line("")
    line("--$boundary--")
    return "multipart/form-data; boundary=$boundary" to out.toByteArray()
}
