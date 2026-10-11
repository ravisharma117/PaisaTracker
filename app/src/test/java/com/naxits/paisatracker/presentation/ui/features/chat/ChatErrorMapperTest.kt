package com.naxits.paisatracker.presentation.ui.features.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Covers [ChatErrorMapper], which exists to stop raw native/class-loading failures from
 * leaking into the chat UI. Regression guard for the `catch (Exception)` → `catch (Throwable)`
 * fix in `LiteRtLmServiceImpl.initialize()`: the errors below are `Error`s, not
 * `Exception`s, and previously escaped the backend-fallback loop entirely.
 */
class ChatErrorMapperTest {

    private val engineFailed =
        "AI inference engine failed to load. Please restart the app and try again."
    private val notReady =
        "AI model is not ready yet. Please wait a moment and try again."
    private val unexpectedRestart =
        "An unexpected error occurred. Please restart the app."
    private val unexpectedRetry =
        "An unexpected error occurred. Please try again."

    // ── Linkage failures (Error, not Exception) ──────────────────────────────

    @Test
    fun `UnsatisfiedLinkError maps to engine-failed message`() {
        assertEquals(
            engineFailed,
            ChatErrorMapper.friendly(
                UnsatisfiedLinkError("dlopen failed: library \"liblitertlm_jni.so\" not found")
            )
        )
    }

    @Test
    fun `NoClassDefFoundError with a bare class name maps to engine-failed message`() {
        val e = NoClassDefFoundError("com.google.ai.edge.litertlm.Engine")
        assertEquals(engineFailed, ChatErrorMapper.friendly(e))
    }

    @Test
    fun `ExceptionInInitializerError maps to engine-failed message`() {
        val e = ExceptionInInitializerError("Engine")
        assertEquals(engineFailed, ChatErrorMapper.friendly(e))
    }

    @Test
    fun `linkerError with no message still maps to engine-failed message`() {
        assertEquals(engineFailed, ChatErrorMapper.friendly(UnsatisfiedLinkError()))
    }

    // ── Opaque linker strings carried on ordinary exceptions ─────────────────

    @Test
    fun `dlopen string is treated as an engine load failure`() {
        assertEquals(
            engineFailed,
            ChatErrorMapper.friendly(
                RuntimeException("dlopen failed: cannot locate symbol \"foo\"")
            )
        )
    }

    @Test
    fun `litertlm_jni string is treated as an engine load failure`() {
        assertEquals(
            engineFailed,
            ChatErrorMapper.friendly(RuntimeException("liblitertlm_jni.so: not found"))
        )
    }

    // ── Bare class names used as messages ───────────────────────────────────

    @Test
    fun `bare class name as a message is hidden`() {
        assertEquals(
            unexpectedRestart,
            ChatErrorMapper.friendly(RuntimeException("LlmInference"))
        )
    }

    @Test
    fun `fully qualified class name as a message is hidden`() {
        assertEquals(
            unexpectedRestart,
            ChatErrorMapper.friendly(
                RuntimeException("com.google.mediapipe.tasks.genai.llminference.LlmInference")
            )
        )
    }

    @Test
    fun `nested class name as a message is hidden`() {
        assertEquals(
            unexpectedRestart,
            ChatErrorMapper.friendly(RuntimeException("Engine\$Companion"))
        )
    }

    // ── Messages that must survive untouched ────────────────────────────────

    @Test
    fun `lowercase dotted identifier is not mistaken for a class name`() {
        // The previous regex matched this and hid a useful error.
        assertEquals(
            "connection.timed.out",
            ChatErrorMapper.friendly(RuntimeException("connection.timed.out"))
        )
    }

    @Test
    fun `ordinary sentence with periods is not mistaken for a class name`() {
        val msg = "Something went wrong. Please try again."
        assertEquals(msg, ChatErrorMapper.friendly(RuntimeException(msg)))
    }

    @Test
    fun `domain name is not mistaken for a class name`() {
        val msg = "Could not reach huggingface.co. Check your network."
        assertEquals(msg, ChatErrorMapper.friendly(RuntimeException(msg)))
    }

    @Test
    fun `already-flavoured UI strings are not re-mapped`() {
        val msg = "AI model not downloaded. Go to Settings to download."
        assertEquals(msg, ChatErrorMapper.friendly(RuntimeException(msg)))
    }

    @Test
    fun `not-initialized message gets the wait message`() {
        assertEquals(
            notReady,
            ChatErrorMapper.friendly(
                IllegalStateException("LiteRT-LM not initialized. Call initialize() first.")
            )
        )
    }

    // ── Degenerate messages ─────────────────────────────────────────────────

    @Test
    fun `null message falls back to retry message`() {
        assertEquals(unexpectedRetry, ChatErrorMapper.friendly(RuntimeException()))
    }

    @Test
    fun `blank message falls back to retry message`() {
        assertEquals(unexpectedRetry, ChatErrorMapper.friendly(RuntimeException("   ")))
    }
}
