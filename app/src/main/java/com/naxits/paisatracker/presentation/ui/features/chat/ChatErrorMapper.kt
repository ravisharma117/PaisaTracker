package com.naxits.paisatracker.presentation.ui.features.chat

/**
 * Maps raw [Throwable] messages from the on-device LLM runtime into strings that are
 * meaningful to a user.
 *
 * JNI and class-loading failures routinely surface with a bare class name or an opaque
 * linker string as the exception message (for example a `NoClassDefFoundError` whose
 * message is just the class it failed to load). Neither tells a user anything actionable,
 * so they are replaced with a generic prompt. Human-readable messages pass through
 * untouched so genuine failures remain diagnosable from the UI.
 */
internal object ChatErrorMapper {

    /**
     * Matches a string that is *nothing but* a Java class name — fully qualified
     * (`com.example.Foo`), bare (`Foo`), or nested (`Foo$Companion`).
     *
     * Requires the final segment to start uppercase, which is what distinguishes a real
     * class name from a lowercase dotted identifier such as `connection.timed.out`.
     */
    private val CLASS_NAME_ONLY = Regex("^([a-zA-Z_$][a-zA-Z0-9_$]*\\.)*[A-Z][a-zA-Z0-9_$]*$")

    fun friendly(t: Throwable): String {
        val msg = t.message ?: ""
        return when {
            // LinkageError covers UnsatisfiedLinkError, NoClassDefFoundError and
            // ExceptionInInitializerError — all native/class-loading failures that the
            // user cannot act on beyond restarting.
            t is LinkageError -> ENGINE_FAILED
            // Defensive: no LiteRT-LM native library dlopens these, but a future
            // dependency could reintroduce them.
            msg.contains("dlopen") || msg.contains("litertlm_jni") -> ENGINE_FAILED
            msg.contains("not initialized") -> NOT_READY
            // A message that is only a class name means an init/linkage failure leaked
            // its own class name as the message.
            CLASS_NAME_ONLY.matches(msg) -> UNEXPECTED_RESTART
            msg.isBlank() -> UNEXPECTED_RETRY
            else -> msg
        }
    }

    private const val ENGINE_FAILED =
        "AI inference engine failed to load. Please restart the app and try again."
    private const val NOT_READY =
        "AI model is not ready yet. Please wait a moment and try again."
    private const val UNEXPECTED_RESTART =
        "An unexpected error occurred. Please restart the app."
    private const val UNEXPECTED_RETRY =
        "An unexpected error occurred. Please try again."
}
