package com.ptiters.marketplace.integration;

/** Storage chưa cấu hình (Python: CloudinaryNotConfiguredError). */
public class MediaStorageNotConfiguredException extends RuntimeException {

    public MediaStorageNotConfiguredException() {
        super("Media storage service is not configured.");
    }
}
