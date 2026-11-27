package com.reip.sales.controller;

import com.reip.common.result.Result;
import com.reip.sales.service.SalePriceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * 一房一价（表价/底价）维护
 */
@RestController
@RequestMapping("/api/sales/prices")
@RequiredArgsConstructor
public class SalePriceController {

    private final SalePriceService svc;

    /** 修改房源表价/底价 */
    @PutMapping("/rooms/{roomId}")
    public Result<Void> updatePrice(@PathVariable Long roomId, @RequestBody PriceForm form) {
        svc.updatePrice(roomId, form.getTablePrice(), form.getFloorPrice());
        return Result.ok();
    }

    @Data
    public static class PriceForm {
        private BigDecimal tablePrice;
        private BigDecimal floorPrice;
    }
}
