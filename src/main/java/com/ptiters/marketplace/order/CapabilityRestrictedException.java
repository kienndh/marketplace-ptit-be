package com.ptiters.marketplace.order;

import com.ptiters.marketplace.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Lỗi 403 có mã máy đọc được, body dạng object như Python:
 * {@code {"detail": {"code": "BUY_RESTRICTED", "message": "..."}}}.
 * Được {@link CapabilityRestrictedAdvice} render (ErrorHandler chung chỉ hỗ trợ detail dạng chuỗi).
 */
public class CapabilityRestrictedException extends ApiException {

    public static final String BUY_RESTRICTED = "BUY_RESTRICTED";
    public static final String SELL_RESTRICTED = "SELL_RESTRICTED";

    private final String code;

    public CapabilityRestrictedException(String code, String message) {
        super(HttpStatus.FORBIDDEN, message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static CapabilityRestrictedException buy() {
        return new CapabilityRestrictedException(BUY_RESTRICTED, "Tài khoản của bạn đang bị giới hạn quyền mua hàng.");
    }

    public static CapabilityRestrictedException sell() {
        return new CapabilityRestrictedException(SELL_RESTRICTED, "Tài khoản của bạn đang bị giới hạn quyền bán hàng.");
    }
}
