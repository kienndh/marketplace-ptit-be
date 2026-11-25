package com.ptiters.marketplace.realtime;

import org.springframework.stereotype.Component;

@Component
public class MediaValidator {

    public void validateImage(byte[] data, String contentType, long maxSizeBytes) {
        throw new UnsupportedOperationException("TODO: Implement image validation");
    }

    public void validateVideo(byte[] data, String contentType, long maxSizeBytes) {
        throw new UnsupportedOperationException("TODO: Implement video validation");
    }
}
