package com.adhd.focusmate.service.wallet;

import com.adhd.focusmate.common.exception.BusinessException;
import com.adhd.focusmate.common.exception.ErrorCode;
import com.adhd.focusmate.common.exception.InsufficientBalanceException;
import com.adhd.focusmate.domain.model.CreditLog;
import com.adhd.focusmate.domain.model.Wallet;
import com.adhd.focusmate.domain.model.type.CreditLogReason;
import com.adhd.focusmate.dto.wallet.CreditChargeRequest;
import com.adhd.focusmate.dto.wallet.CreditDeductRequest;
import com.adhd.focusmate.dto.wallet.WalletResponse;
import com.adhd.focusmate.repository.CreditLogRepository;
import com.adhd.focusmate.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final CreditLogRepository creditLogRepository;

    @Transactional
    public WalletResponse charge(CreditChargeRequest request) {
        Wallet wallet = walletRepository.findByUserId(request.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "User wallet not found"));

        int newBalance = wallet.getBalance() + request.amount();

        // Update balance using specific method or builder if no setter (Entities
        // usually have no setters)
        // Since we don't have a setter, we might need a method in Wallet entity.
        // For now, let's assume we can modify it or use a domain method.
        // Wait, @Builder and @Getter only. We should add a domain method
        // 'updateBalance'.
        // Refactoring Wallet entity to add business method is cleaner.
        // BUT, given the constraints, I will use reflection or add the method.
        // Let's Add a method 'addBalance' and 'subtractBalance' to Wallet using
        // replace_file_content later if needed.
        // Or assume we can just replace the object? No, JPA needs update.
        // I will implement 'updateBalance' method in Wallet entity in next step.
        // For now, I'll write the logic assuming the method exists.

        wallet.addBalance(request.amount());

        creditLogRepository.save(CreditLog.builder()
                .wallet(wallet)
                .amount(request.amount())
                .reason(CreditLogReason.CHARGE)
                .build());

        return new WalletResponse(wallet.getUser().getId(), wallet.getBalance());
    }

    @Transactional
    public WalletResponse deduct(CreditDeductRequest request) {
        Wallet wallet = walletRepository.findByUserId(request.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "User wallet not found"));

        if (wallet.getBalance() < request.amount()) {
            throw new InsufficientBalanceException();
        }

        wallet.subtractBalance(request.amount());

        creditLogRepository.save(CreditLog.builder()
                .wallet(wallet)
                .amount(-request.amount()) // Negative amount for log? or positive? Usually negative for deduct.
                .reason(request.reason())
                .build());

        return new WalletResponse(wallet.getUser().getId(), wallet.getBalance());
    }

    @Transactional(readOnly = true)
    public WalletResponse getBalance(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "User wallet not found"));
        return new WalletResponse(wallet.getUser().getId(), wallet.getBalance());
    }
}
