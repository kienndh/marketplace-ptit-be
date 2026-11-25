package com.ptiters.marketplace.user;

import java.util.*;

public final class SupportedBanksRegistry {

    private SupportedBanksRegistry() {
    }

    public static final List<SupportedBank> SUPPORTED_BANKS = List.of(
            new SupportedBank("970436", "VCB", "Vietcombank", "Ngân hàng TMCP Ngoại Thương Việt Nam"),
            new SupportedBank("970415", "CTG", "VietinBank", "Ngân hàng TMCP Công thương Việt Nam"),
            new SupportedBank("970418", "BIDV", "BIDV", "Ngân hàng TMCP Đầu tư và Phát triển Việt Nam"),
            new SupportedBank("970405", "VBA", "Agribank", "Ngân hàng Nông nghiệp và Phát triển Nông thôn Việt Nam"),
            new SupportedBank("970422", "MB", "MBBank", "Ngân hàng TMCP Quân đội"),
            new SupportedBank("970407", "TCB", "Techcombank", "Ngân hàng TMCP Kỹ thương Việt Nam"),
            new SupportedBank("970416", "ACB", "ACB", "Ngân hàng TMCP Á Châu"),
            new SupportedBank("970432", "VPB", "VPBank", "Ngân hàng TMCP Việt Nam Thịnh Vượng"),
            new SupportedBank("970423", "TPB", "TPBank", "Ngân hàng TMCP Tiên Phong"),
            new SupportedBank("970403", "STB", "Sacombank", "Ngân hàng TMCP Sài Gòn Thương Tín"),
            new SupportedBank("970437", "HDB", "HDBank", "Ngân hàng TMCP Phát triển TP.HCM"),
            new SupportedBank("970441", "VIB", "VIB", "Ngân hàng TMCP Quốc tế Việt Nam"),
            new SupportedBank("970443", "SHB", "SHB", "Ngân hàng TMCP Sài Gòn - Hà Nội"),
            new SupportedBank("970440", "SSB", "SeABank", "Ngân hàng TMCP Đông Nam Á"),
            new SupportedBank("970426", "MSB", "MSB", "Ngân hàng TMCP Hàng Hải Việt Nam"),
            new SupportedBank("970448", "OCB", "OCB", "Ngân hàng TMCP Phương Đông"),
            new SupportedBank("970449", "LPB", "LPBank", "Ngân hàng TMCP Lộc Phát Việt Nam"),
            new SupportedBank("970409", "BAB", "BacABank", "Ngân hàng TMCP Bắc Á"),
            new SupportedBank("970412", "PVB", "PVcomBank", "Ngân hàng TMCP Đại Chúng Việt Nam"),
            new SupportedBank("970419", "NCB", "NCB", "Ngân hàng TMCP Quốc Dân"),
            new SupportedBank("970424", "SHBVN", "ShinhanBank", "Ngân hàng TNHH MTV Shinhan Việt Nam"),
            new SupportedBank("970429", "SCB", "SCB", "Ngân hàng TMCP Sài Gòn"),
            new SupportedBank("970430", "PGB", "PGBank", "Ngân hàng TMCP Thịnh vượng và Phát triển"),
            new SupportedBank("970427", "VAB", "VietABank", "Ngân hàng TMCP Việt Á"),
            new SupportedBank("970428", "NAB", "NamABank", "Ngân hàng TMCP Nam Á"),
            new SupportedBank("970452", "KLB", "KienLongBank", "Ngân hàng TMCP Kiên Long"),
            new SupportedBank("970438", "BVB", "BaoVietBank", "Ngân hàng TMCP Bảo Việt"),
            new SupportedBank("970400", "SGB", "SaigonBank", "Ngân hàng TMCP Sài Gòn Công Thương"),
            new SupportedBank("970454", "BVBANK", "BVBank", "Ngân hàng TMCP Bản Việt"),
            new SupportedBank("970433", "VBB", "VietBank", "Ngân hàng TMCP Việt Nam Thương Tín"),
            new SupportedBank("970444", "CBB", "CBBank", "Ngân hàng Thương mại TNHH MTV Xây dựng Việt Nam"),
            new SupportedBank("970414", "OJB", "OceanBank", "Ngân hàng Thương mại TNHH MTV Đại Dương"),
            new SupportedBank("970408", "GPB", "GPBank", "Ngân hàng Thương mại TNHH MTV Dầu Khí Toàn Cầu"),
            new SupportedBank("970457", "WVN", "WooriBank", "Ngân hàng TNHH MTV Woori Việt Nam"),
            new SupportedBank("970421", "VRB", "VRB", "Ngân hàng Liên doanh Việt - Nga"),
            new SupportedBank("970439", "PBVN", "PublicBank", "Ngân hàng TNHH MTV Public Việt Nam"),
            new SupportedBank("970442", "HLBVN", "HongLeong", "Ngân hàng TNHH MTV Hong Leong Việt Nam"),
            new SupportedBank("970455", "IBKHN", "IBK - Hanoi", "Ngân hàng Công nghiệp Hàn Quốc - Chi nhánh Hà Nội"),
            new SupportedBank("970456", "IBKHCM", "IBK - HCM", "Ngân hàng Công nghiệp Hàn Quốc - Chi nhánh TP.HCM"),
            new SupportedBank("801011", "NHB", "Nonghyup Bank", "Ngân hàng Nonghyup - Chi nhánh Hà Nội"),
            new SupportedBank("668888", "KBANK", "KBank", "Ngân hàng Kasikornbank - Chi nhánh TP.HCM"),
            new SupportedBank("546034", "CAKE", "CAKE by VPBank", "Ngân hàng số CAKE by VPBank"),
            new SupportedBank("963388", "TIMO", "Timo by BanVietBank", "Ngân hàng số Timo by BanVietBank"),
            new SupportedBank("971005", "VTLMONEY", "Viettel Money", "Tổng Công ty Dịch vụ Số Viettel"),
            new SupportedBank("971011", "VNPTMONEY", "VNPT Money", "Tập đoàn Bưu chính Viễn thông Việt Nam")
    );

    private static final Map<String, SupportedBank> BANKS_BY_BIN = new HashMap<>();
    private static final Map<String, SupportedBank> BANKS_BY_CODE = new HashMap<>();

    static {
        for (SupportedBank bank : SUPPORTED_BANKS) {
            BANKS_BY_BIN.put(bank.getBin(), bank);
            BANKS_BY_CODE.put(bank.getCode().toUpperCase(Locale.ROOT), bank);
        }
    }

    public static List<SupportedBank> getAllBanks() {
        return SUPPORTED_BANKS;
    }

    public static Optional<SupportedBank> getBankByBin(String bin) {
        if (bin == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BANKS_BY_BIN.get(bin.trim()));
    }

    public static Optional<SupportedBank> getBankByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BANKS_BY_CODE.get(code.trim().toUpperCase(Locale.ROOT)));
    }

    public static Optional<String> resolveBankBin(String identifier) {
        if (identifier == null) {
            return Optional.empty();
        }
        String cleanId = identifier.trim();
        if (BANKS_BY_BIN.containsKey(cleanId)) {
            return Optional.of(cleanId);
        }
        String upperCode = cleanId.toUpperCase(Locale.ROOT);
        if (BANKS_BY_CODE.containsKey(upperCode)) {
            return Optional.of(BANKS_BY_CODE.get(upperCode).getBin());
        }
        return Optional.empty();
    }
}
