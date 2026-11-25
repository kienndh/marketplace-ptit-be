package com.ptiters.marketplace.integration;

/** Upload/truy vấn storage thất bại (Python: CloudinaryUploadError). */
public class MediaStorageException extends RuntimeException {

    public MediaStorageException(String message) {
        super(message);
    }

    public MediaStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
