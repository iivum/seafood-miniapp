package com.seafood.user.domain.model;

/**
 * Exception thrown when WeChat login or phone number decryption fails.
 * This encompasses failures from the WeChat API such as invalid codes,
 * session key expiration, or data decryption errors.
 *
 * <p>Users of this service should catch this exception and present
 * a user-friendly message prompting them to retry the WeChat authentication.</p>
 *
 * @see WxJavaService
 */
public class WeChatLoginException extends RuntimeException {

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message describing the login failure
     */
    public WeChatLoginException(String message) {
        super(message);
    }

    /**
     * Constructs a new exception with the specified detail message and cause.
     * The cause is typically a WxErrorException from the WeChat SDK.
     *
     * @param message the detail message describing the login failure
     * @param cause   the underlying exception that caused this failure
     */
    public WeChatLoginException(String message, Throwable cause) {
        super(message, cause);
    }
}
