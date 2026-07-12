package com.focuskeeper.reboot.common.error;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.dao.DataIntegrityViolationException;

@RestController
public class TestExceptionController {

    @GetMapping("/test/business")
    public String business() {
        throw new BusinessException(ErrorCode.COMMON_BAD_REQUEST);
    }

    @GetMapping("/test/runtime")
    public String runtime() {
        throw new IllegalStateException("unexpected");
    }

    @GetMapping("/test/carryover-conflict")
    public String carryoverConflict() {
        throw new DataIntegrityViolationException(
                "duplicate key violates unique constraint uq_big3_items_derived_from_item"
        );
    }
}
