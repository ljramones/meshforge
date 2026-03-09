package org.dynamisengine.meshforge.mgi;

/**
 * MGI structural validation failure.
 */
public final class MgiValidationException extends RuntimeException {
    public MgiValidationException(String message) {
        super(message);
    }
}
