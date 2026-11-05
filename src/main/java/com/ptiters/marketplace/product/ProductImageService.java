
package com.ptiters.marketplace.product;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.ptiters.marketplace.exception.ApiException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

        Map<?, ?> result;

        try {
            result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "marketplace/products/" + productId,
                            "resource_type", "image"
                    )
            );
        } catch (IOException e) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Image upload failed."
            );
        }

        String imageUrl = (String) result.get("secure_url");
        String publicId = (String) result.get("public_id");

        try {
            return transactionTemplate.execute(status -> {

                Product currentProduct = productRepository
                        .findByProductIdAndDeletedFalse(productId)
                        .orElseThrow(() ->
                                ApiException.notFound("Product not found."));

                if (!currentProduct.getSellerId().equals(sellerId)) {
                    throw ApiException.forbidden(
                            "You do not own this product."
                    );
                }

                List<ProductImage> images = productImageRepository
                        .findByProduct_ProductIdOrderBySortOrderAscImageIdAsc(
                                productId
                        );

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
                    // Log cleanup failure in production.
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
                .findByProductIdAndDeletedFalse(productId)
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
        String publicId = transactionTemplate.execute(status -> {

            Product product = productRepository
                    .findByProductIdAndDeletedFalse(productId)
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

            productImageRepository.delete(image);

            return image.getCloudinaryPublicId();
        });

        if (publicId == null || publicId.isBlank()) {
            log.warn("Image {} has no Cloudinary public ID", imageId);
            return;
        }

        try {
            Map<?, ?> result = cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.asMap("resource_type", "image")
            );

            Object state = result.get("result");

            if (!"ok".equals(state) && !"not found".equals(state)) {
                log.warn(
                        "Cloudinary delete returned {} for image {}",
                        state,
                        imageId
                );
            }

        } catch (Exception e) {
            log.error(
                    "Failed to delete Cloudinary image: {}",
                    publicId,
                    e
            );
        }
    }

}
