package cn.dtkeys.dataservice.interfaces.controller;

import cn.dtkeys.dataservice.application.service.PlatformBaseService;
import cn.dtkeys.dataservice.infrastructure.security.PermissionCode;
import cn.dtkeys.dataservice.infrastructure.security.RequirePermission;
import cn.dtkeys.dataservice.interfaces.response.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * P0 运行底座检查接口。
 */
@Validated
@RestController
@RequestMapping("/api/platform")
@RequirePermission(PermissionCode.PLATFORM_BASELINE_VIEW)
public class PlatformBaseController {

    private final PlatformBaseService platformBaseService;

    public PlatformBaseController(PlatformBaseService platformBaseService) {
        this.platformBaseService = platformBaseService;
    }

    /**
     * 返回平台基础运行摘要。
     */
    @GetMapping("/baseline")
    public ApiResponse<PlatformBaseService.PlatformBaseSummary> getBaseline() {
        return ApiResponse.success(platformBaseService.getPlatformBaseSummary());
    }
}
