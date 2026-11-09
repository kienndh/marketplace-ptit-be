
package com.ptiters.marketplace.product.image;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.product.Product;
import com.ptiters.marketplace.product.ProductRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@AllArgsConstructor
public class ProductImageService {

    private ProductRepository productRepository;
    private ProductImageRepository productImageRepository;
    private Cloudinary cloudinary;
    private ModelMapper modelMapper;
    private TransactionTemplate transactionTemplate;
    private final CloudinaryCleanupTaskRepository cleanupTaskRepository;

    private static final int MAX_IMAGES = 8;
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;

    private Map<?, ?> uploadToCloudinary(
            Long productId,
            MultipartFile file
    ) {
        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "marketplace/products/" + productId,
                            "resource_type", "image"
                    )
            );

            String imageUrl = (String) result.get("secure_url");
            String publicId = (String) result.get("public_id");

            if (imageUrl == null || imageUrl.isBlank()
                    || publicId == null || publicId.isBlank()) {

                log.error(
                        "Cloudinary returned incomplete upload response"
                );

                if (publicId != null && !publicId.isBlank()) {
                    try {
                        cloudinary.uploader().destroy(
                                publicId,
                                ObjectUtils.asMap(
                                        "resource_type", "image"
                                )
                        );
                    } catch (Exception cleanupError) {
                        log.error(
                                "Failed to clean up incomplete upload: {}",
                                publicId,
                                cleanupError
                        );
                    }
                }

                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "Invalid Cloudinary upload response."
                );
            }

            return result;

        } catch (IOException e) {
            log.error("Cloudinary upload I/O failure", e);

            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Image upload failed."
            );

        }catch (RuntimeException e) {
            if (e instanceof ApiException) {
                throw e;
            }

            log.error("Unexpected Cloudinary upload failure", e);

            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "Image storage service is unavailable."
            );
        }
    }


    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Image is required.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw ApiException.badRequest("Image exceeds 5MB.");
        }

        try {
            byte[] bytes = file.getBytes();

            boolean jpeg = bytes.length >= 3
                    && (bytes[0] & 0xFF) == 0xFF
                    && (bytes[1] & 0xFF) == 0xD8
                    && (bytes[2] & 0xFF) == 0xFF;

            boolean png = bytes.length >= 8
                    && (bytes[0] & 0xFF) == 0x89
                    && bytes[1] == 0x50
                    && bytes[2] == 0x4E
                    && bytes[3] == 0x47
                    && bytes[4] == 0x0D
                    && bytes[5] == 0x0A
                    && bytes[6] == 0x1A
                    && bytes[7] == 0x0A;

            boolean webp = bytes.length >= 12
                    && bytes[0] == 'R'
                    && bytes[1] == 'I'
                    && bytes[2] == 'F'
                    && bytes[3] == 'F'
                    && bytes[8] == 'W'
                    && bytes[9] == 'E'
                    && bytes[10] == 'B'
                    && bytes[11] == 'P';

            if (!jpeg && !png && !webp) {
                throw ApiException.badRequest(
                        "Only JPEG, PNG and WebP images are allowed."
                );
            }

        } catch (IOException e) {
            throw ApiException.badRequest("Cannot read image file.");
        }
    }


    public List<ProductImageResponse> getProductImages(Long productId) {

        Product product = productRepository
                .findByProductIdAndDeletedFalse(productId)
                .orElseThrow(() ->
                        ApiException.notFound("Product not found.")
                );

        if (!"AVAILABLE".equals(product.getStatus())
                || !"CLEAR".equals(product.getModerationStatus())) {
            throw ApiException.notFound("Product not found.");
        }

        List<ProductImage> images = productImageRepository
                .findByProduct_ProductIdOrderBySortOrderAscImageIdAsc(
                        productId
                );

        return images.stream()
                .map(image -> {
                    ProductImageResponse response = new ProductImageResponse();

                    response.setImageId(image.getImageId());
                    response.setProductId(productId);
                    response.setImageUrl(image.getImageUrl());
                    response.setSortOrder(image.getSortOrder());
                    response.setThumbnail(image.getThumbnail());

                    return response;
                })
                .toList();
    }



    public ProductImageResponse uploadImage(
            Long productId,
            Long sellerId,
            MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Image is required.");
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            throw ApiException.badRequest("Image exceeds 5MB.");
        }

        Set<String> allowedTypes = Set.of(
                "image/jpeg",
                "image/png",
                "image/webp"
        );

        if (!allowedTypes.contains(file.getContentType())) {
            throw ApiException.badRequest("Invalid image type.");
        }

        Product product = productRepository
                .findByProductIdAndDeletedFalse(productId)
                .orElseThrow(() ->
                        ApiException.notFound("Product not found."));

        if (!product.getSellerId().equals(sellerId)) {
            throw ApiException.forbidden(
                    "You do not own this product."
            );
        }

        Map<?, ?> result = uploadToCloudinary(productId, file);

        String imageUrl = (String) result.get("secure_url");
        String publicId = (String) result.get("public_id");

        try {
            return transactionTemplate.execute(status -> {

                Product currentProduct = productRepository
                        .findByIdForUpdate(productId)
                        .orElseThrow(() ->
                                ApiException.notFound("Product not found.")
                        );


                if (!currentProduct.getSellerId().equals(sellerId)) {
                    throw ApiException.forbidden(
                            "You do not own this product."
                    );
                }

                List<ProductImage> images = productImageRepository
                        .findByProduct_ProductIdOrderBySortOrderAscImageIdAsc(productId);

                if (images.size() >= MAX_IMAGES) {
                    throw ApiException.badRequest(
                            "Maximum 8 images per product."
                    );
                }

                int nextOrder = images.isEmpty()
                        ? 0
                        : images.getLast().getSortOrder() + 1;

                if (nextOrder > Short.MAX_VALUE) {
                    throw ApiException.unprocessable(
                            "Image order limit exceeded."
                    );
                }

                ProductImage image = new ProductImage();

                image.setProduct(currentProduct);
                image.setImageUrl(imageUrl);
                image.setCloudinaryPublicId(publicId);
                image.setSortOrder((short) nextOrder);
                image.setThumbnail(images.isEmpty());

                ProductImage saved = productImageRepository.save(image);


                ProductImageResponse response = new ProductImageResponse();

                response.setImageId(saved.getImageId());
                response.setProductId(saved.getProduct().getProductId());
                response.setImageUrl(saved.getImageUrl());
                response.setSortOrder(saved.getSortOrder());
                response.setThumbnail(saved.getThumbnail());

                return response;

            });

        } catch (RuntimeException e) {
            if (publicId != null) {
                try {
                    cloudinary.uploader().destroy(
                            publicId,
                            ObjectUtils.asMap("resource_type", "image")
                    );
                } catch (Exception cleanupError) {
                    log.error(
                            "Failed to clean up Cloudinary image: {}",
                            publicId,
                            cleanupError
                    );
                }

            }

            throw e;
        }
    }

    @Transactional
    public ProductImageResponse setThumbnail(
            Long productId,
            Long imageId,
            Long sellerId
    ) {
        Product product = productRepository
                .findByIdForUpdate(productId)
                .orElseThrow(() ->
                        ApiException.notFound("Product not found.")
                );

        if (!product.getSellerId().equals(sellerId)) {
            throw ApiException.forbidden(
                    "You do not own this product."
            );
        }

        ProductImage image = productImageRepository
                .findByImageIdAndProduct_ProductId(imageId, productId)
                .orElseThrow(() ->
                        ApiException.notFound("Image not found.")
                );

        List<ProductImage> images = productImageRepository
                .findByProduct_ProductIdOrderBySortOrderAscImageIdAsc(productId);

        for (ProductImage item : images) {
            item.setThumbnail(item.getImageId().equals(imageId));
        }

        productImageRepository.saveAll(images);

        ProductImageResponse response = new ProductImageResponse();

        response.setImageId(image.getImageId());
        response.setProductId(productId);
        response.setImageUrl(image.getImageUrl());
        response.setSortOrder(image.getSortOrder());
        response.setThumbnail(image.getThumbnail());

        return response;
    }


    public void deleteImage(
            Long productId,
            Long imageId,
            Long sellerId
    ) {
        transactionTemplate.executeWithoutResult(status -> {

            Product product = productRepository
                    .findByIdForUpdate(productId)
                    .orElseThrow(() ->
                            ApiException.notFound("Product not found.")
                    );

            if (!product.getSellerId().equals(sellerId)) {
                throw ApiException.forbidden(
                        "You do not own this product."
                );
            }

            ProductImage image = productImageRepository
                    .findByImageIdAndProduct_ProductId(imageId, productId)
                    .orElseThrow(() ->
                            ApiException.notFound("Image not found.")
                    );

            if (Boolean.TRUE.equals(image.getThumbnail())) {
                productImageRepository
                        .findByProduct_ProductIdOrderBySortOrderAscImageIdAsc(
                                productId
                        )
                        .stream()
                        .filter(i -> !i.getImageId().equals(imageId))
                        .findFirst()
                        .ifPresent(i -> i.setThumbnail(true));
            }

            String publicId = image.getCloudinaryPublicId();

            productImageRepository.delete(image);

            if (publicId != null && !publicId.isBlank()) {
                CloudinaryCleanupTask task =
                        cleanupTaskRepository.findByPublicId(publicId)
                                .orElseGet(CloudinaryCleanupTask::new);

                if (task.getTaskId() == null
                        || !"COMPLETED".equals(task.getStatus())) {

                    task.setPublicId(publicId);
                    task.setStatus("PENDING");
                    task.setRetryCount(0);
                    task.setMaxRetries(5);
                    task.setNextRetryAt(LocalDateTime.now());
                    task.setLastError(null);

                    cleanupTaskRepository.save(task);
                }
            }
        });
    }


}
