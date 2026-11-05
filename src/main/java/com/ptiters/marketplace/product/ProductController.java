
package com.ptiters.marketplace.product;

import com.ptiters.marketplace.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@AllArgsConstructor
public class ProductController {

    private ProductService productService;
    private final ProductImageService productImageService;

    @GetMapping("/me")
    public List<ProductResponse> getMyProducts(
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        return productService.getMyProducts(principal.getUserId());
    }


    @GetMapping
    public ProductListResponse getAllProducts(
            @Valid @ModelAttribute ProductFilterForm form,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor
    ) {
        return productService.getPublicProducts(
                form,
                limit,
                cursor
        );
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable("id") Long id) {
        return productService.getProductById(id);
    }

    @GetMapping("/{id}/images")
    public List<ProductImageResponse> getProductImages(
            @PathVariable("id") Long productId
    ) {
        return productImageService.getProductImages(productId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody CreateProductRequest request
    ) {
        return productService.createProduct(
                principal.getUserId(),
                request
        );
    }


    @PostMapping(
            value = "/{id}/images/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public ProductImageResponse uploadProductImage(
            @PathVariable("id") Long productId,
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestPart("file") MultipartFile file
    ) {
        return productImageService.uploadImage(
                productId,
                user.getUserId(),
                file
        );
    }


    @PatchMapping("/{id}")
    public ProductResponse updateProduct(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        return productService.updateProduct(
                id,
                principal.getUserId(),
                request
        );
    }


    @PatchMapping("/{productId}/images/{imageId}/thumbnail")
    public ProductImageResponse setProductThumbnail(
            @PathVariable Long productId,
            @PathVariable Long imageId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return productImageService.setThumbnail(
                productId,
                imageId,
                user.getUserId()
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal AuthenticatedUser principal
    ) {
        productService.deleteProduct(
                id,
                principal.getUserId()
        );
    }


    @DeleteMapping("/{productId}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProductImage(
            @PathVariable Long productId,
            @PathVariable Long imageId,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        productImageService.deleteImage(
                productId,
                imageId,
                user.getUserId()
        );
    }

}
