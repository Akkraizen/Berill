package dev.akkraizen.berill.server

import dev.akkraizen.berill.server.tokens.TokenCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TokenCodecTest {
  @Test
  fun tokensAreLongRandomAndHashedStably() {
    val a = TokenCodec.generate()
    val b = TokenCodec.generate()
    assertNotEquals(a, b)
    assertTrue(a.startsWith(TokenCodec.PREFIX))
    assertEquals(TokenCodec.PREFIX.length + 43, a.length)
    assertEquals(64, TokenCodec.hash(a).length)
    assertEquals(TokenCodec.hash(a), TokenCodec.hash(" $a "))
    assertNotEquals(TokenCodec.hash(a), TokenCodec.hash(b))
    assertTrue(TokenCodec.USER.matches("akkraizen"))
    assertTrue(!TokenCodec.USER.matches("bad user"))
  }
}
