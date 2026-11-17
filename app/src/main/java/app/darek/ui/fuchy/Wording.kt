package app.darek.ui.fuchy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.darek.R
import app.darek.fuchy.Clash
import app.darek.fuchy.Permission
import app.darek.fuchy.Refusal
import app.darek.fuchy.SentValue
import app.darek.listen.Persona
import app.darek.theme.Spacing

/** The badge or the warning, told apart by icon and words as well as colour. */
@Composable
fun Approval(
    approved: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (approved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.hair),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (approved) Icons.Filled.CheckCircle else Icons.Filled.Warning, contentDescription = null, tint = color)
        Text(
            stringResource(if (approved) R.string.fuchy_approved else R.string.fuchy_unapproved),
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
    }
}

/** [nameOf] turns a fucha's ID into its name. */
@Composable
fun clashText(
    clash: Clash,
    nameOf: (String) -> String,
): String =
    when (clash) {
        is Clash.Holds ->
            stringResource(
                R.string.clash_holds,
                clash.candidate.phrase,
                clash.enabled.phrase,
                nameOf(clash.enabled.fucha),
            )
        is Clash.SoundsLike ->
            stringResource(
                R.string.clash_sounds_like,
                clash.candidate.phrase,
                clash.enabled.phrase,
                nameOf(clash.enabled.fucha),
            )
        is Clash.NearName -> stringResource(R.string.clash_near_name, clash.word, clash.persona.label())
        is Clash.NearWord ->
            stringResource(
                R.string.clash_near_word,
                clash.word,
                clash.near,
                nameOf(clash.enabled.fucha),
            )
    }

/** [phraseOf] turns a Command's ID into its phrase. */
@Composable
fun refusalText(
    refusal: Refusal,
    phraseOf: (String) -> String,
): String =
    when (refusal) {
        is Refusal.NeedsNewerDarek -> stringResource(R.string.refusal_newer_darek)
        Refusal.Unreadable -> stringResource(R.string.refusal_unreadable)
        is Refusal.ReservedId -> stringResource(R.string.refusal_reserved_id, refusal.id)
        is Refusal.BadId -> stringResource(R.string.refusal_bad_id, refusal.id)
        is Refusal.WrongLanguage -> stringResource(R.string.refusal_wrong_language, refusal.language)
        is Refusal.Undeclared ->
            stringResource(
                R.string.refusal_undeclared,
                phraseOf(refusal.command),
                stringResource(refusal.permission.labelRes()),
            )
        is Refusal.BadAddress -> stringResource(R.string.refusal_bad_address, refusal.address)
        is Refusal.NoReply -> stringResource(R.string.refusal_no_reply, phraseOf(refusal.command))
        is Refusal.EmptyPhrase -> stringResource(R.string.refusal_empty_phrase, refusal.command)
        is Refusal.CancelWord -> stringResource(R.string.refusal_cancel_word, phraseOf(refusal.command), refusal.word)
        is Refusal.UnknownWords -> stringResource(R.string.refusal_unknown_words, refusal.words.joinToString(", "))
    }

fun Permission.labelRes(): Int =
    when (this) {
        Permission.CALL -> R.string.permission_call
        Permission.INTERNET -> R.string.permission_internet
    }

fun SentValue.labelRes(): Int =
    when (this) {
        SentValue.CHOSEN_CITY -> R.string.sent_chosen_city
    }

@Composable
private fun Persona.label(): String =
    stringResource(
        when (this) {
            Persona.DAREK -> R.string.persona_darek
            Persona.DARIA -> R.string.persona_daria
        },
    )
