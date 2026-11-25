package com.ptiters.marketplace.integration;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CloudinaryStorageService {

    public String uploadImage(byte[] data, String folder, String publicId) {
        throw new UnsupportedOperationException("TODO: Implement Cloudinary image upload");
    }

    public String uploadVideo(byte[] data, String folder, String publicId) {
        throw new UnsupportedOperationException("TODO: Implement Cloudinary video upload");
    }

    public boolean destroyAsset(String publicId, String resourceType) {
        throw new UnsupportedOperationException("TODO: Implement Cloudinary asset destruction");
    }

    public String generateDeliveryUrl(String publicId, String transformation) {
        throw new UnsupportedOperationException("TODO: Implement Cloudinary delivery URL generation");
    }

    public Map<String, Object> generateUploadSignature(String folder, String publicId) {
        throw new UnsupportedOperationException("TODO: Implement Cloudinary upload signature generation");
    }
}
