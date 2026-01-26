package com.adhd.focusmate.repository;

import com.adhd.focusmate.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
