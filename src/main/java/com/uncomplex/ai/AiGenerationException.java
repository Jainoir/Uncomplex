package com.uncomplex.ai;

/** The AI provider failed to produce a usable roadmap (network error, refusal, or repeated invalid output). */
public class AiGenerationException extends RuntimeException {

    public AiGenerationException(String message) {
        super(message);
    }

    public AiGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
