package com.focuskeeper.reboot.common.error;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
