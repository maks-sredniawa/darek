package app.darek.fuchy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.util.Base64

const val FORMAT = 1

/**
 * A `.darek` file's outer JSON. The fucha travels as base64 so that a signature covers its exact
 * bytes and no JSON has to be canonicalised to verify it.
 */
@Serializable
private class Envelope(
    val fucha: String,
    val signature: String? = null,
)

/** A fucha as written, its list values still names: an unknown one is a refusal, not a parse error. */
@Serializable
data class FuchaSource(
    val format: Int,
    val id: String,
    val version: Int,
    val language: String,
    val name: String,
    val description: String,
    val author: String,
    val profanity: Boolean,
    val permissions: List<String> = emptyList(),
    val addresses: List<String> = emptyList(),
    val sends: List<String> = emptyList(),
    val commands: List<CommandSource>,
)

@Serializable
data class CommandSource(
    val id: String,
    val phrase: String,
    val action: String,
    val reply: String? = null,
    @SerialName("reply_feminine") val replyFeminine: String? = null,
    val decoys: List<String> = emptyList(),
)

sealed interface Parsed {
    data class Read(
        val source: FuchaSource,
        val signature: String?,
    ) : Parsed

    data class Refused(
        val refusal: Refusal,
    ) : Parsed
}

private val json = Json

/** The format is read before the rest, so a newer file is told it needs a newer Darek rather than that it is broken. */
fun parse(file: ByteArray): Parsed =
    try {
        val envelope = json.decodeFromString<Envelope>(file.utf8())
        val tree = json.parseToJsonElement(Base64.getDecoder().decode(envelope.fucha).utf8()) as? JsonObject
        val format = (tree?.get("format") as? JsonPrimitive)?.intOrNull
        when {
            tree == null || format == null || format < FORMAT -> Parsed.Refused(Refusal.Unreadable)
            format > FORMAT -> Parsed.Refused(Refusal.NewerFormat(format))
            else -> Parsed.Read(json.decodeFromJsonElement<FuchaSource>(tree), envelope.signature)
        }
    } catch (e: IllegalArgumentException) {
        Parsed.Refused(Refusal.Unreadable)
    } catch (e: CharacterCodingException) {
        Parsed.Refused(Refusal.Unreadable)
    }

private fun ByteArray.utf8(): String =
    Charsets.UTF_8
        .newDecoder()
        .decode(ByteBuffer.wrap(this))
        .toString()
