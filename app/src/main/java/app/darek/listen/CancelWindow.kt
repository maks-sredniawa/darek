package app.darek.listen

/**
 * Whether a Command that calls dials (ADR-0011). Once it is anything but [Decision.WAIT] the decision
 * stands, whatever follows. Synchronized because *Zatrzymaj* arrives on the main thread.
 */
class CancelWindow {
    enum class Decision { WAIT, DIAL, CANCELLED, STOPPED, CANNOT_HEAR }

    private var decision = Decision.WAIT
    private var recordingStartedAt: Long? = null

    @Synchronized
    fun recordingStarted(nowMs: Long): Decision {
        if (decision == Decision.WAIT && recordingStartedAt == null) recordingStartedAt = nowMs
        return decision
    }

    @Synchronized
    fun heard(
        tokens: List<String>,
        nowMs: Long,
    ): Decision = decide(nowMs, cancelWord = tokens.any { it in Grammar.CANCEL_WORDS })

    @Synchronized
    fun tick(nowMs: Long): Decision = decide(nowMs, cancelWord = false)

    /** The microphone stopped hearing: the cancel word could not be heard, so the dial is refused. */
    @Synchronized
    fun cannotHear(): Decision {
        if (decision == Decision.WAIT) decision = Decision.CANNOT_HEAR
        return decision
    }

    @Synchronized
    fun listeningStopped(): Decision {
        if (decision == Decision.WAIT) decision = Decision.STOPPED
        return decision
    }

    private fun decide(
        nowMs: Long,
        cancelWord: Boolean,
    ): Decision {
        val startedAt = recordingStartedAt
        if (decision == Decision.WAIT && startedAt != null) {
            decision =
                when {
                    nowMs - startedAt > LENGTH_MS -> Decision.DIAL
                    cancelWord -> Decision.CANCELLED
                    else -> Decision.WAIT
                }
        }
        return decision
    }

    private companion object {
        const val LENGTH_MS = 2_000L
    }
}
