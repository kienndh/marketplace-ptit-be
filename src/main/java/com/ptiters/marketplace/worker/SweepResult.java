package com.ptiters.marketplace.worker;

/**
 * Kết quả một chu kỳ sweep của worker (Python: {@code SweepResult}).
 *
 * @param changed    số bản ghi đã được xử lý thành công
 * @param candidates số bản ghi ứng viên
 * @param noOp       số bản ghi bỏ qua vì không còn cần xử lý
 * @param failed     số bản ghi lỗi
 */
public record SweepResult(int changed, int candidates, int noOp, int failed) {

    public static SweepResult empty() {
        return new SweepResult(0, 0, 0, 0);
    }
}
