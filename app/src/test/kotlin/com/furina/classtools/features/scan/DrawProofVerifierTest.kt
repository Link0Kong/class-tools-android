package com.furina.classtools.features.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawProofVerifierTest {

    private val seed = "0123456789abcdef"

    @Test
    fun `replay matches web draw-v1 without duplicates`() {
        val replayed = DrawProofVerifier.replay((1..10).toList(), 5, allowDuplicates = false, seed = seed)
        assertEquals(listOf(9, 10, 4, 6, 7), replayed)
    }

    @Test
    fun `replay matches web draw-v1 with duplicates`() {
        val replayed = DrawProofVerifier.replay((1..10).toList(), 5, allowDuplicates = true, seed = seed)
        assertEquals(listOf(1, 6, 2, 3, 4), replayed)
    }

    @Test
    fun `replay of single item pool`() {
        assertEquals(listOf(7), DrawProofVerifier.replay(listOf(7), 3, allowDuplicates = false, seed = seed))
    }

    @Test
    fun `empty pool or count returns empty`() {
        assertEquals(emptyList<Int>(), DrawProofVerifier.replay(emptyList(), 5, false, seed))
        assertEquals(emptyList<Int>(), DrawProofVerifier.replay(listOf(1, 2), 0, false, seed))
    }

    @Test
    fun `sha256 matches known vector`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            DrawProofVerifier.sha256Hex("abc"),
        )
    }

    @Test
    fun `hash and replay match helpers`() {
        val canon = "v1\nid\n1"
        val hash = DrawProofVerifier.sha256Hex(canon)
        assertTrue(DrawProofVerifier.hashMatches(canon, hash))
        assertTrue(DrawProofVerifier.hashMatches(canon, hash.uppercase()))
        assertFalse(DrawProofVerifier.hashMatches(canon, "0".repeat(64)))
        assertFalse(DrawProofVerifier.hashMatches("", hash))
        assertFalse(DrawProofVerifier.hashMatches(canon, ""))
        assertTrue(
            DrawProofVerifier.replayMatches(
                pool = (1..10).toList(),
                count = 5,
                allowDuplicates = false,
                seed = seed,
                result = listOf(9, 10, 4, 6, 7),
            ),
        )
        assertFalse(
            DrawProofVerifier.replayMatches(
                pool = (1..10).toList(),
                count = 5,
                allowDuplicates = false,
                seed = seed,
                result = listOf(1, 2, 3, 4, 5),
            ),
        )
    }
}
