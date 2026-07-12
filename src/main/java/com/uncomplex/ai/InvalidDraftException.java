package com.uncomplex.ai;

/** The model returned schema-valid JSON whose content still violates our generation rules. */
public class InvalidDraftException extends RuntimeException {

    public InvalidDraftException(String message) {
        super(message);
    }
}
