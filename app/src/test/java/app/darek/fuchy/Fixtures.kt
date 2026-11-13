package app.darek.fuchy

import kotlinx.serialization.json.Json
import java.io.File
import java.util.Base64

val ownFuchaFile: ByteArray get() = File("src/main/assets/${OwnFucha.ASSET}").readBytes()

val ownFucha: Fucha by lazy { OwnFucha.from(ownFuchaFile) }

fun ownCommand(id: String) = ownFucha.commands.single { it.id == id }

val weather =
    FuchaSource(
        format = 1,
        id = "pogoda",
        version = 1,
        language = "pl",
        name = "Pogoda",
        description = "Darek mówi, jaka jest pogoda.",
        author = "MaksForge",
        profanity = false,
        permissions = listOf("internet"),
        addresses = listOf("https://api.open-meteo.com/v1/forecast"),
        sends = listOf("chosen_city"),
        commands =
            listOf(
                CommandSource(
                    id = "jaka-pogoda",
                    phrase = "jaka pogoda",
                    action = "speak",
                    reply = "Słonecznie.",
                    decoys = listOf("jaka przygoda"),
                ),
            ),
    )

fun FuchaSource.withCommand(command: CommandSource) = copy(commands = listOf(command))

fun envelope(
    inner: ByteArray,
    signature: String? = null,
): ByteArray {
    val fucha = Base64.getEncoder().encodeToString(inner)
    val signed = signature?.let { ""","signature":"$it"""" } ?: ""
    return """{"fucha":"$fucha"$signed}""".toByteArray()
}

fun fileOf(
    source: FuchaSource,
    signature: String? = null,
): ByteArray = envelope(Json.encodeToString(source).toByteArray(), signature)

val knowsAll: (String) -> Boolean = { true }

fun accepted(
    source: FuchaSource,
    knows: (String) -> Boolean = knowsAll,
): Fucha = (checked(fileOf(source), knows) as Checked.Accepted).fucha
