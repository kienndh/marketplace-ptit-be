package com.ptiters.marketplace.user;

import com.ptiters.marketplace.bank.Bank;
import com.ptiters.marketplace.bank.BankCatalog;
import com.ptiters.marketplace.exception.ApiException;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@AllArgsConstructor
public class BankAccountService {

    private UserRepository userRepository;
    private BankCatalog bankCatalog;

    public List<Bank> getSupportedBanks() {
        return bankCatalog.findAll();
    }

    // Trả null nếu người dùng chưa cấu hình tài khoản nhận tiền
    @Transactional(readOnly = true)
    public BankAccountResponse getBankAccount(Long userId) {
        User user = findUser(userId);
        if (user.getBankBin() == null || user.getBankAccountNumber() == null) {
            return null;
        }
        return toResponse(user);
    }

    @Transactional
    public BankAccountResponse saveBankAccount(Long userId, UpdateBankAccountRequest request) {
        // Chấp nhận BIN hoặc mã ngân hàng, luôn lưu BIN chuẩn
        Bank bank = bankCatalog.findByBinOrCode(request.getBankBin())
                .orElseThrow(() -> ApiException.unprocessable("Unsupported bank_bin or bank code."));

        User user = findUser(userId);
        user.setBankBin(bank.getBin());
        user.setBankAccountNumber(request.getAccountNumber());
        user.setBankAccountName(request.getAccountName().trim());
        userRepository.saveAndFlush(user);
        return toResponse(user);
    }

    @Transactional
    public void deleteBankAccount(Long userId) {
        User user = findUser(userId);
        user.setBankBin(null);
        user.setBankAccountNumber(null);
        user.setBankAccountName(null);
        userRepository.saveAndFlush(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found."));
    }

    private BankAccountResponse toResponse(User user) {
        Bank bank = bankCatalog.findByBin(user.getBankBin()).orElse(null);
        return new BankAccountResponse(
                user.getBankBin(),
                bank == null ? null : bank.getCode(),
                bank == null ? null : bank.getName(),
                bank == null ? null : bank.getShortName(),
                user.getBankAccountNumber(),
                user.getBankAccountName());
    }
}