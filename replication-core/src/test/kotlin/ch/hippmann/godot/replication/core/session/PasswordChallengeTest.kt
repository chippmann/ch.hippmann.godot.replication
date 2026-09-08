package ch.hippmann.godot.replication.core.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordChallengeTest {

    @Test
    fun `the right password proves itself against a fresh nonce`() {
        val verifier = PasswordVerifier.forPassword("wrench-42")
        val nonce = PasswordChallenge.randomBytes(PasswordChallenge.NONCE_SIZE)
        val proof = PasswordChallenge.proofFor("wrench-42", verifier.salt, nonce)
        assertTrue(verifier.matches(nonce, proof))
    }

    @Test
    fun `a wrong password or a replayed proof fails`() {
        val verifier = PasswordVerifier.forPassword("wrench-42")
        val nonce = PasswordChallenge.randomBytes(PasswordChallenge.NONCE_SIZE)
        val otherNonce = PasswordChallenge.randomBytes(PasswordChallenge.NONCE_SIZE)
        assertFalse(verifier.matches(nonce, PasswordChallenge.proofFor("crowbar-7", verifier.salt, nonce)))
        assertFalse(verifier.matches(otherNonce, PasswordChallenge.proofFor("wrench-42", verifier.salt, nonce)))
        assertFalse(verifier.matches(nonce, ByteArray(0)))
    }

    @Test
    fun `salts and nonces are random`() {
        assertFalse(PasswordVerifier.forPassword("same").salt.contentEquals(PasswordVerifier.forPassword("same").salt))
        assertEquals(PasswordChallenge.SALT_SIZE, PasswordVerifier.forPassword("same").salt.size)
    }
}
