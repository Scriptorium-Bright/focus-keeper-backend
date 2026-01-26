package com.adhd.focusmate.domain.model;

import com.adhd.focusmate.domain.common.BaseEntity;
import com.adhd.focusmate.domain.model.type.ProviderType;
import com.adhd.focusmate.domain.model.type.RoleType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
@Table(name = "users")
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "nickname")
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    private ProviderType provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private RoleType role;
}
