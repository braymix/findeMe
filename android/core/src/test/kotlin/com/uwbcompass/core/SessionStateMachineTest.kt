package com.uwbcompass.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionStateMachineTest {

    @Test
    fun `outgoing happy path`() {
        val m = SessionStateMachine()
        m.transition(SessionState.INVITED)
        m.transition(SessionState.ACCEPTED)
        m.transition(SessionState.NEGOTIATED)
        m.transition(SessionState.ACTIVE)
        m.transition(SessionState.ENDED)
        assertTrue(m.isTerminal)
    }

    @Test
    fun `incoming happy path`() {
        val m = SessionStateMachine()
        m.transition(SessionState.INCOMING)
        m.transition(SessionState.ACCEPTED)
        m.transition(SessionState.NEGOTIATED)
        m.transition(SessionState.ACTIVE)
        assertEquals(SessionState.ACTIVE, m.state)
    }

    @Test
    fun `illegal transition throws`() {
        val m = SessionStateMachine()
        assertFailsWith<IllegalStateException> { m.transition(SessionState.ACTIVE) }
    }

    @Test
    fun `cannot leave a terminal state except via reset`() {
        val m = SessionStateMachine()
        m.transition(SessionState.INCOMING)
        m.transition(SessionState.DECLINED)
        assertTrue(m.isTerminal)
        assertFalse(m.canTransition(SessionState.ACTIVE))
        m.resetIfTerminal()
        assertEquals(SessionState.IDLE, m.state)
    }

    @Test
    fun `active can fail on peer disconnect`() {
        val m = SessionStateMachine()
        m.transition(SessionState.INVITED)
        m.transition(SessionState.ACCEPTED)
        m.transition(SessionState.NEGOTIATED)
        m.transition(SessionState.ACTIVE)
        m.transition(SessionState.FAILED)
        assertTrue(m.isTerminal)
    }
}
