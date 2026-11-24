package com.unesp.desktopsimulator.app.services.client;

/** A sessão não pode mais ser renovada (refresh token expirado/revogado): é preciso logar de novo. */
public class SessionExpiredException extends RuntimeException {

    public SessionExpiredException(String message) {
        super(message);
    }

    public SessionExpiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
