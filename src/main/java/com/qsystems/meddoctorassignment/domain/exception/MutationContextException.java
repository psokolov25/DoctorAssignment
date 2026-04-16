package com.qsystems.meddoctorassignment.domain.exception;

/**
 * Сигнализирует, что Orchestra отклонила mutating workflow из-за неактивного или
 * неполного operator/entrypoint context.
 */
public class MutationContextException extends RuntimeException {

    public MutationContextException(String message) {
        super(message);
    }

    public MutationContextException(String message, Throwable cause) {
        super(message, cause);
    }
}
