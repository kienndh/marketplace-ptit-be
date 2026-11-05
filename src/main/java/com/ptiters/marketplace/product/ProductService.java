
package com.ptiters.marketplace.product;

import com.ptiters.marketplace.category.CategoryRepository;
import com.ptiters.marketplace.exception.ApiException;
import com.ptiters.marketplace.user.User;
import com.ptiters.marketplace.user.UserRepository;
import com.ptiters.marketplace.user.UserStatus;
import jakarta.persistence.EntityManager;
import lombok.AllArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
@AllArgsConstructor
public class ProductService {

    private ProductRepository productRepository;
    private CategoryRepository categoryRepository;
    private UserRepository userRepository;
    private ModelMapper modelMapper;
    private EntityManager entityManager;
    private final ProductImageRepository productImageRepository;


    @Transactional(readOnly = true)
    public List<ProductResponse> getMyProducts(Long sellerId) {

        List<Product> products = productRepository
                .findBySellerIdAndDeletedFalseOrderByCreatedAtDesc(sellerId);

        return products.stream()
                .map(product ->
                        modelMapper.map(product, ProductResponse.class))
                .toList();
    }


    @Transactional(readOnly = true)
    public ProductListResponse getPublicProducts(
            ProductFilterForm form,
            Integer limit,
            String cursor
    ) {
        if (limit == null) {
            limit = 20;
        }

        if (limit < 1 || limit > 50) {
            throw ApiException.unprocessable(
                    "Limit must be between 1 and 50."
            );
        }

        if (form != null) {
            if (form.getMinPrice() != null
                    && form.getMaxPrice() != null
                    && form.getMinPrice().compareTo(form.getMaxPrice()) > 0) {
                throw ApiException.unprocessable(
                        "Minimum price must not exceed maximum price."
                );
            }

            if (form.getListingType() != null
                    && !form.getListingType().equals("FIXED_PRICE")
                    && !form.getListingType().equals("AUCTION")) {
                throw ApiException.unprocessable("Invalid listing type.");
            }
        }

        LocalDateTime cursorTime = null;
        Long cursorId = null;

        if (cursor != null) {
            try {
                String decoded = new String(
                        Base64.getUrlDecoder().decode(cursor),
                        StandardCharsets.UTF_8
                );

                String[] parts = decoded.split("\\|", -1);

                if (parts.length != 2) {
                    throw new IllegalArgumentException();
                }

                cursorTime = LocalDateTime.parse(parts[0]);
                cursorId = Long.parseLong(parts[1]);

                if (cursorId <= 0) {
                    throw new IllegalArgumentException();
                }

            } catch (IllegalArgumentException e) {
                throw ApiException.unprocessable("Invalid cursor.");
            }
        }

        Specification<Product> spec =
                ProductSpecification.buildSpec(form, cursorTime, cursorId);

        Pageable pageable = PageRequest.of(
                0,
                limit + 1,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("productId")
                )
        );

        List<Product> products = productRepository
                .findAll(spec, pageable)
                .getContent();

        boolean hasMore = products.size() > limit;

        List<Product> currentPage = hasMore
                ? products.subList(0, limit)
                : products;

        List<ProductResponse> items = currentPage.stream()
                .map(product ->
                        modelMapper.map(product, ProductResponse.class))
                .toList();

        String nextCursor = null;

        if (hasMore && !currentPage.isEmpty()) {
            Product lastProduct = currentPage.getLast();

            String rawCursor = lastProduct.getCreatedAt()
                    + "|" + lastProduct.getProductId();

            nextCursor = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(
                            rawCursor.getBytes(StandardCharsets.UTF_8)
                    );
        }

        return new ProductListResponse(items, nextCursor, hasMore);
    }



    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long productId) {

        Product product = productRepository
                .findByProductIdAndDeletedFalse(productId)
                .orElseThrow(() ->
                        ApiException.notFound("Product not found."));

        if (!"AVAILABLE".equals(product.getStatus())
                || !"CLEAR".equals(product.getModerationStatus())) {
            throw ApiException.notFound("Product not found.");
        }

        User seller = userRepository.findById(product.getSellerId())
                .orElseThrow(() ->
                        ApiException.notFound("Product not found."));

        if (seller.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.notFound("Product not found.");
        }


        ProductResponse response = modelMapper.map(
                product,
                ProductResponse.class
        );

        List<ProductImageResponse> images = productImageRepository
                .findByProduct_ProductIdOrderBySortOrderAscImageIdAsc(productId)
                .stream()
                .map(image -> {
                    ProductImageResponse dto = new ProductImageResponse();

                    dto.setImageId(image.getImageId());
                    dto.setProductId(productId);
                    dto.setImageUrl(image.getImageUrl());
                    dto.setSortOrder(image.getSortOrder());
                    dto.setThumbnail(image.getThumbnail());

                    return dto;
                })
                .toList();

        response.setImages(images);

        return response;

    }


    @Transactional
    public ProductResponse createProduct(
            Long sellerId,
            CreateProductRequest request
    ) {
        User seller = userRepository.findById(sellerId)
                .orElseThrow(() ->
                        ApiException.notFound("User not found."));

        if (seller.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden("User is not active.");
        }

        if (seller.getPhone() == null ||
                seller.getPhone().isBlank()) {
            throw ApiException.unprocessable(
                    "Please update your phone number before selling."
            );
        }

        categoryRepository
                .findByCategoryIdAndDeletedFalse(request.getCategoryId())
                .orElseThrow(() ->
                        ApiException.notFound("Category not found."));

        Product product = new Product();

        product.setSellerId(sellerId);
        product.setCategoryId(request.getCategoryId());
        product.setTitle(request.getTitle().trim());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStock(request.getStock());

        product.setListingType("FIXED_PRICE");
        product.setStatus("AVAILABLE");
        product.setModerationStatus("CLEAR");
        product.setReservedStock(0);
        product.setViewCount(0);
        product.setDeleted(false);

        Product savedProduct = productRepository.saveAndFlush(product);

        entityManager.refresh(savedProduct);

        return modelMapper.map(savedProduct, ProductResponse.class);
    }


    @Transactional
    public ProductResponse updateProduct(
            Long productId,
            Long sellerId,
            UpdateProductRequest request
    ) {
        Product product = productRepository
                .findByProductIdAndDeletedFalse(productId)
                .orElseThrow(() ->
                        ApiException.notFound("Product not found."));

        if (!product.getSellerId().equals(sellerId)) {
            throw ApiException.forbidden(
                    "You do not own this product."
            );
        }

        if (request.getTitle() != null) {
            String title = request.getTitle().trim();

            if (title.isEmpty()) {
                throw ApiException.unprocessable(
                        "Title must not be blank."
                );
            }

            product.setTitle(title);
        }

        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }

        if (request.getPrice() != null) {
            product.setPrice(request.getPrice());
        }

        if (request.getStock() != null) {
            if (request.getStock() < product.getReservedStock()) {
                throw ApiException.unprocessable(
                        "Stock cannot be less than reserved stock."
                );
            }

            product.setStock(request.getStock());

            if (request.getStock() == 0) {
                product.setStatus("SOLD_OUT");
            } else if ("SOLD_OUT".equals(product.getStatus())) {
                product.setStatus("AVAILABLE");
            }
        }

        productRepository.saveAndFlush(product);

        entityManager.refresh(product);

        return modelMapper.map(product, ProductResponse.class);
    }


    @Transactional
    public void deleteProduct(Long productId, Long sellerId) {

        Product product = productRepository
                .findByProductIdAndDeletedFalse(productId)
                .orElseThrow(() ->
                        ApiException.notFound("Product not found."));

        if (!product.getSellerId().equals(sellerId)) {
            throw ApiException.forbidden(
                    "You do not own this product."
            );
        }

        if (product.getReservedStock() > 0) {
            throw ApiException.unprocessable(
                    "Cannot delete a product with reserved stock."
            );
        }

        product.setDeleted(true);

        productRepository.save(product);
    }

}
