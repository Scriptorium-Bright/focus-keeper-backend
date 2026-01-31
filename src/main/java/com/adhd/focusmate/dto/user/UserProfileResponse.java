package com.adhd.focusmate.dto.user;

import com.adhd.focusmate.domain.model.User;
import com.adhd.focusmate.domain.model.type.ProviderType;
import com.adhd.focusmate.domain.model.type.RoleType;

import java.io.Serializable;

/**
 * 사용자 프로필 응답 DTO
 * - Redis 캐시 저장을 위해 Serializable 구현
 */
public record UserProfileResponse(
        Long id,
        String email,
        String nickname,
        ProviderType provider,
        RoleType role) implements Serializable {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProvider(),
                user.getRole());
    }
}
