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

    /**
     * 잔고 충전
     * @param request
     * @return
     */
    @Transactional
    public WalletResponse charge(CreditChargeRequest request) {
        Wallet wallet = findWalletByUserId(request.userId());

        wallet.addBalance(request.amount());

        creditLogRepository.save(CreditLog.builder()
                .wallet(wallet)
                .amount(request.amount())
                .reason(CreditLogReason.CHARGE)
                .build());

        return toResponse(wallet);
    }

    /**
     * 잔고를 감소시키는 메소드 (검증 및 에러처리는 Domain에 존재)
     *
     * @param request
     * @return
     */
    @Transactional
    public WalletResponse deduct(CreditDeductRequest request) {
        Wallet wallet = findWalletByUserId(request.userId());

        wallet.subtractBalance(request.amount());

        creditLogRepository.save(CreditLog.builder()
                .wallet(wallet)
                .amount(-request.amount())
                .reason(request.reason())
                .build());

        return toResponse(wallet);
    }

    @Transactional(readOnly = true)
    public WalletResponse getBalance(Long userId) {
        Wallet wallet = findWalletByUserId(userId);
        return toResponse(wallet);
    }

    // ===== Private Helper Methods =====

    private Wallet findWalletByUserId(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "User wallet not found"));
    }

    private WalletResponse toResponse(Wallet wallet) {
        return WalletResponse.from(wallet);
    }
}
