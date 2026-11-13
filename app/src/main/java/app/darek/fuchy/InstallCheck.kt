package app.darek.fuchy

import app.darek.listen.Command
import app.darek.listen.Grammar
import app.darek.listen.Line
import java.net.URI
import java.net.URISyntaxException

/** Why a fucha is not installed (ADR-0015, phase-F.md section 2). The install screen words each one. */
sealed interface Refusal {
    /** Shown as *„Wymaga nowszej wersji Darka"*. */
    sealed interface NeedsNewerDarek : Refusal

    data class NewerFormat(
        val format: Int,
    ) : NeedsNewerDarek

    data class UnknownName(
        val list: NamedList,
        val name: String,
    ) : NeedsNewerDarek

    data class OwnOnly(
        val command: String,
        val action: Action,
    ) : NeedsNewerDarek

    data object Unreadable : Refusal

    /** Only Darek's own, inside the APK, is `darek`: any other would read as an update of it. */
    data class ReservedId(
        val id: String,
    ) : Refusal

    data class WrongLanguage(
        val language: String,
    ) : Refusal

    data class Undeclared(
        val command: String,
        val permission: Permission,
    ) : Refusal

    data class BadAddress(
        val address: String,
    ) : Refusal

    /** A Command with nothing to say: every action this Darek has speaks the fucha's Reply. */
    data class NoReply(
        val command: String,
    ) : Refusal

    /** With no words of its own, the Command would fire on the Persona's name alone. */
    data class EmptyPhrase(
        val command: String,
    ) : Refusal

    /** Cancel words act on partial results, so saying it would cancel any call in its window (ADR-0011). */
    data class CancelWord(
        val command: String,
        val word: String,
    ) : Refusal

    /** Vosk drops a grammar word its model does not know, and the Command could never fire. */
    data class UnknownWords(
        val words: List<String>,
    ) : Refusal
}

enum class NamedList { ACTION, PERMISSION, SENT_VALUE }

const val LANGUAGE = "pl"

/** Every reason to refuse [source]; [knows] says whether the recogniser's model has a word (`listen/Vocabulary`). */
fun refusals(
    source: FuchaSource,
    knows: (String) -> Boolean,
    bundled: Boolean = false,
): List<Refusal> =
    buildList {
        if (source.format != FORMAT) add(Refusal.NewerFormat(source.format))
        if (source.language != LANGUAGE) add(Refusal.WrongLanguage(source.language))
        if (source.id == OwnFucha.ID && !bundled) add(Refusal.ReservedId(source.id))
        val declared = source.permissions.mapNotNull { named<Permission>(it) }
        source.permissions
            .filter { named<Permission>(it) == null }
            .forEach { add(Refusal.UnknownName(NamedList.PERMISSION, it)) }
        source.sends
            .filter { named<SentValue>(it) == null }
            .forEach { add(Refusal.UnknownName(NamedList.SENT_VALUE, it)) }
        source.addresses.filterNot(::isAllowedAddress).forEach { add(Refusal.BadAddress(it)) }
        source.commands.forEach { command ->
            val action = named<Action>(command.action)
            if (action == null) {
                add(Refusal.UnknownName(NamedList.ACTION, command.action))
            } else {
                action.needs?.takeIf { it !in declared }?.let { add(Refusal.Undeclared(command.id, it)) }
                if (action.ownOnly && !bundled) add(Refusal.OwnOnly(command.id, action))
                if (command.reply.isNullOrBlank() || command.replyFeminine?.isBlank() == true) {
                    add(Refusal.NoReply(command.id))
                }
            }
            val words = wordsOf(command.phrase)
            if (words.isEmpty()) add(Refusal.EmptyPhrase(command.id))
            words.firstOrNull { it in Grammar.CANCEL_WORDS }?.let { add(Refusal.CancelWord(command.id, it)) }
        }
        val unknown =
            source.commands
                .flatMap { command -> wordsOf(command.phrase) + command.decoys.flatMap(::wordsOf) }
                .distinct()
                .filterNot(knows)
        if (unknown.isNotEmpty()) add(Refusal.UnknownWords(unknown))
    }

/** HTTPS to one exact host, a path prefix at most: nothing that could widen or hide where a request goes. */
fun isAllowedAddress(address: String): Boolean {
    val uri =
        try {
            URI(address)
        } catch (e: URISyntaxException) {
            return false
        }
    return uri.scheme == "https" &&
        !uri.host.isNullOrEmpty() &&
        uri.rawUserInfo == null &&
        uri.port == -1 &&
        uri.rawQuery == null &&
        uri.rawFragment == null
}

sealed interface Checked {
    data class Accepted(
        val fucha: Fucha,
    ) : Checked

    data class Refused(
        val refusals: List<Refusal>,
    ) : Checked
}

fun checked(
    file: ByteArray,
    knows: (String) -> Boolean,
    bundled: Boolean = false,
): Checked =
    when (val parsed = parse(file)) {
        is Parsed.Refused -> Checked.Refused(listOf(parsed.refusal))
        is Parsed.Read ->
            refusals(parsed.source, knows, bundled)
                .takeIf { it.isNotEmpty() }
                ?.let(Checked::Refused)
                ?: Checked.Accepted(parsed.source.toFucha(parsed.signature, bundled))
    }

private fun FuchaSource.toFucha(
    signature: String?,
    bundled: Boolean,
) = Fucha(
    id = id,
    version = version,
    name = name,
    description = description,
    author = author,
    profanity = profanity,
    permissions = permissions.mapNotNull { named<Permission>(it) },
    addresses = addresses,
    sends = sends.mapNotNull { named<SentValue>(it) },
    commands =
        commands.map {
            Command(
                id = it.id,
                fucha = id,
                phrase = normalised(it.phrase),
                action = checkNotNull(named<Action>(it.action)),
                reply = Line(checkNotNull(it.reply), it.replyFeminine ?: it.reply),
                decoys = it.decoys.map(::normalised),
                bundled = bundled,
            )
        },
    bundled = bundled,
    signature = signature,
)
