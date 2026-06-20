package com.focuskeeper.reboot.recovery.planning.dto;

import org.springframework.web.bind.annotation.RequestBody;

public record Big3ItemRequest(String userId,
                              String big3ItemId)
{

}
