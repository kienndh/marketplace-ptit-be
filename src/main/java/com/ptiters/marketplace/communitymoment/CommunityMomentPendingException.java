package com.ptiters.marketplace.communitymoment;

/**
 * Exception cho lỗi anti-spam 409 Conflict khi người dùng đã có 1 ảnh ở trạng thái PENDING.
 * Giữ payload JSON khớp với Python & React frontend:
 * {@code {"detail": {"code": "COMMUNITY_MOMENT_PENDING", "message": "..."}}}.
 */
public class CommunityMomentPendingException extends RuntimeException {

    public static final String CODE = "COMMUNITY_MOMENT_PENDING";

    public CommunityMomentPendingException(String message) {
        super(message);
    }
}
