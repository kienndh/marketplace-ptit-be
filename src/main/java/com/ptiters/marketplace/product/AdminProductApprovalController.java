package com.ptiters.marketplace.product;

import com.ptiters.marketplace.user.CurrentUserResolver;
import com.ptiters.marketplace.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/product-approvals")
@RequiredArgsConstructor
public class AdminProductApprovalController {

    private final ProductService productService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ResponseEntity<AdminProductApprovalListResponse> listAdminProductApprovals(
            @RequestParam(value = "state", defaultValue = "PENDING") String state,
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            @RequestParam(value = "cursor", required = false) String cursor
    ) {
        currentUserResolver.requireAdmin();
        AdminProductApprovalListResponse response = productService.listAdminApprovals(state, limit, cursor);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<AdminProductApprovalDetailResponse> getAdminProductApprovalDetail(@PathVariable Long productId) {
        currentUserResolver.requireAdmin();
        AdminProductApprovalDetailResponse response = productService.getAdminApprovalDetail(productId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{productId}/decision")
    public ResponseEntity<AdminProductApprovalDetailResponse> adminProductApprovalDecision(
            @PathVariable Long productId,
            @RequestBody AdminApprovalDecisionRequest request
    ) {
        User admin = currentUserResolver.requireAdmin();
        AdminProductApprovalDetailResponse response = productService.adminDecision(productId, admin, request);
        return ResponseEntity.ok(response);
    }
}
