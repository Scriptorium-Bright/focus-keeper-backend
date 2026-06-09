package com.focuskeeper.reboot.recovery.planning.controller;

import com.focuskeeper.reboot.common.response.ApiResponse;
import com.focuskeeper.reboot.recovery.planning.dto.*;
import com.focuskeeper.reboot.recovery.planning.service.Big3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/recovery")
@Tag(name = "Recovery", description = "Recovery loop planning and execution APIs")
/**
 * 오늘의 Big3 선택 API를 노출하는 컨트롤러다.
 */
public class Big3Controller {

    private final Big3Service big3Service;

    public Big3Controller(Big3Service big3Service) {
        this.big3Service = big3Service;
    }

    /**
     * 사용자가 고른 inbox item들을 오늘의 Big3로 확정한다.
     */
    @PostMapping("/big3")
    @Operation(summary = "Select today's Big3", description = "Picks up to three inbox items as today's recovery priorities.")
    public ApiResponse<DailyBig3BoardResponse> selectBig3(
            @Valid @RequestBody SelectBig3Request request
    ) {

        DailyBig3BoardResponse dailyBig3Board = big3Service.selectTodayBig3(request.userId(), request.itemIds());

        return ApiResponse.success(dailyBig3Board, "BIG3_SELECTED");
    }

    @GetMapping("/big3")
    public ApiResponse<DailyBig3BoardResponse> checkBig3(
            @RequestParam("userId") String userId
    ) {
        DailyBig3BoardResponse todayBig3 = big3Service.getTodayBig3(userId);

        return ApiResponse.success(todayBig3, "check big3");
    }

    @PostMapping("/expired")
    @Scheduled(cron = "0 30 0 * * *")
    public ApiResponse<?> expiredTask() {
        big3Service.expireLastWeekTasks();
        return ApiResponse.success("만료 작업 성공");
    }

    // dailyBig3BoardResponse 를 응답으로 넣는게 맞는가?
    @PostMapping("/continue")
    public ApiResponse<DailyBig3BoardResponse> continueWork(@RequestBody DailyBig3BoardRequest request) {

        DailyBig3BoardResponse dailyBig3BoardResponse = big3Service.continueLastWeekWork(
                request.userId(),
                request.big3ItemIds()
        );

        return ApiResponse.success(dailyBig3BoardResponse, "이월 작업을 완료하였습니다.");
    }

    @PostMapping("/abandon")
    public ApiResponse<Big3ItemResponse> abandonItem(
            @RequestBody String userId,
            @RequestBody String big3ItemId
    ) {

        Big3ItemResponse big3ItemResponse = big3Service.abandonItem(userId, big3ItemId);

        return ApiResponse.success(big3ItemResponse, "abandon big3Item");
    }



}
