package com.ptiters.marketplace.bank;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class BankCatalog {

    private List<Bank> banks = List.of(
            new Bank("970436", "VCB", "Vietcombank", "Ngân hàng TMCP Ngoại thương Việt Nam"),
            new Bank("970415", "ICB", "VietinBank", "Ngân hàng TMCP Công thương Việt Nam"),
            new Bank("970418", "BIDV", "BIDV", "Ngân hàng TMCP Đầu tư và Phát triển Việt Nam"),
            new Bank("970405", "VBA", "Agribank", "Ngân hàng Nông nghiệp và Phát triển Nông thôn Việt Nam"),
            new Bank("970422", "MB", "MBBank", "Ngân hàng TMCP Quân đội"),
            new Bank("970407", "TCB", "Techcombank", "Ngân hàng TMCP Kỹ thương Việt Nam"),
            new Bank("970416", "ACB", "ACB", "Ngân hàng TMCP Á Châu"),
            new Bank("970432", "VPB", "VPBank", "Ngân hàng TMCP Việt Nam Thịnh Vượng"),
            new Bank("970423", "TPB", "TPBank", "Ngân hàng TMCP Tiên Phong"),
            new Bank("970403", "STB", "Sacombank", "Ngân hàng TMCP Sài Gòn Thương Tín"),
            new Bank("970437", "HDB", "HDBank", "Ngân hàng TMCP Phát triển Thành phố Hồ Chí Minh"),
            new Bank("970441", "VIB", "VIB", "Ngân hàng TMCP Quốc tế Việt Nam"),
            new Bank("970443", "SHB", "SHB", "Ngân hàng TMCP Sài Gòn - Hà Nội"),
            new Bank("970426", "MSB", "MSB", "Ngân hàng TMCP Hàng Hải Việt Nam"),
            new Bank("970448", "OCB", "OCB", "Ngân hàng TMCP Phương Đông"),
            new Bank("970440", "SEAB", "SeABank", "Ngân hàng TMCP Đông Nam Á"),
            new Bank("970431", "EIB", "Eximbank", "Ngân hàng TMCP Xuất Nhập khẩu Việt Nam"),
            new Bank("970449", "LPB", "LPBank", "Ngân hàng TMCP Lộc Phát Việt Nam"),
            new Bank("970425", "ABB", "ABBANK", "Ngân hàng TMCP An Bình"),
            new Bank("970428", "NAB", "NamABank", "Ngân hàng TMCP Nam Á"),
            new Bank("970429", "SCB", "SCB", "Ngân hàng TMCP Sài Gòn"),
            new Bank("970409", "BAB", "BacABank", "Ngân hàng TMCP Bắc Á"),
            new Bank("970412", "PVCB", "PVcomBank", "Ngân hàng TMCP Đại Chúng Việt Nam"),
            new Bank("970419", "NCB", "NCB", "Ngân hàng TMCP Quốc Dân")
    );

    public List<Bank> findAll() {
        return banks;
    }

    // Nhận mã BIN (970422) hoặc mã ngân hàng (MB, không phân biệt hoa thường)
    public Optional<Bank> findByBinOrCode(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String key = value.trim();
        return banks.stream()
                .filter(b -> b.getBin().equals(key) || b.getCode().equalsIgnoreCase(key))
                .findFirst();
    }

    public Optional<Bank> findByBin(String bin) {
        return banks.stream().filter(b -> b.getBin().equals(bin)).findFirst();
    }
}