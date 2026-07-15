package com.uwbcompass.core

/** Client-side mirror of the backend session state machine (ADR-0007). */
enum class SessionState { IDLE, INVITED, INCOMING, ACCEPTED, NEGOTIATED, ACTIVE, ENDED, DECLINED, EXPIRED, FAILED }

/**
 * Reduces server events into the client's local session state and guards against
 * illegal transitions. `IDLE` = no session; `INVITED` = we invited someone (outgoing);
 * `INCOMING` = someone invited us. Both funnel into ACCEPTED/NEGOTIATED/ACTIVE.
 */
class SessionStateMachine(initial: SessionState = SessionState.IDLE) {
    var state: SessionState = initial
        private set

    private val allowed: Map<SessionState, Set<SessionState>> = mapOf(
        SessionState.IDLE to setOf(SessionState.INVITED, SessionState.INCOMING),
        SessionState.INVITED to setOf(SessionState.ACCEPTED, SessionState.DECLINED, SessionState.EXPIRED, SessionState.FAILED, SessionState.ENDED),
        SessionState.INCOMING to setOf(SessionState.ACCEPTED, SessionState.DECLINED, SessionState.EXPIRED, SessionState.FAILED, SessionState.ENDED),
        SessionState.ACCEPTED to setOf(SessionState.NEGOTIATED, SessionState.EXPIRED, SessionState.FAILED, SessionState.ENDED),
        SessionState.NEGOTIATED to setOf(SessionState.ACTIVE, SessionState.EXPIRED, SessionState.FAILED, SessionState.ENDED),
        SessionState.ACTIVE to setOf(SessionState.ENDED, SessionState.FAILED),
        SessionState.ENDED to emptySet(),
        SessionState.DECLINED to emptySet(),
        SessionState.EXPIRED to emptySet(),
        SessionState.FAILED to emptySet(),
    )

    val isTerminal: Boolean
        get() = state in setOf(SessionState.ENDED, SessionState.DECLINED, SessionState.EXPIRED, SessionState.FAILED)

    fun canTransition(to: SessionState): Boolean = to in (allowed[state] ?: emptySet())

    /** @throws IllegalStateException on an illegal transition. */
    fun transition(to: SessionState): SessionState {
        check(canTransition(to)) { "illegal client transition $state -> $to" }
        state = to
        return state
    }

    /** Resets to IDLE from any terminal state (returning to the peer list). */
    fun resetIfTerminal() {
        if (isTerminal) state = SessionState.IDLE
    }
}
