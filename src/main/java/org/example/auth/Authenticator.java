package org.example.auth;

public interface Authenticator<T> {
    /**
     * Performs authentication and returns an authenticated client or token.
     * @return an object representing the authenticated session (e.g., token or client instance).
     * @throws Exception if authentication fails.
     */
    T authenticate() throws Exception;
}
