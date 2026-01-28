package com.adhd.focusmate.dto.wallet;

import com.adhd.focusmate.domain.model.Wallet;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "지갑 정보 응답 DTO")
public record WalletResponse(
                @Schema(description = "지갑 ID") Long id,

                @Schema(description = "사용자 ID") Long userId,

                @Schema(description = "잔액 (예치금)", example = "10000") Integer balance,

                @Schema(description = "포인트 (보상/아이템 구매용)", example = "500") Long point) {
        public static WalletResponse from(Wallet wallet) {
                return new WalletResponse(
                                wallet.getId(),
                                wallet.getUser().getId(),
                                wallet.getBalance(),
                                wallet.getPoint());
        }
}
