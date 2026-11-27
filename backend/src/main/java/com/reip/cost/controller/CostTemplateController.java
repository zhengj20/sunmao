package com.reip.cost.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.cost.entity.CostContractTemplate;
import com.reip.cost.service.CostTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 合同模板：上传/列表/删除 + 按模板生成合同
 */
@RestController
@RequestMapping("/api/cost/templates")
@RequiredArgsConstructor
public class CostTemplateController {

    private final CostTemplateService svc;

    @GetMapping("/page")
    public Result<PageResult<CostContractTemplate>> page(@RequestParam(defaultValue = "1") long current,
                                                          @RequestParam(defaultValue = "50") long size,
                                                          @RequestParam(required = false) String contractType,
                                                          @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, contractType, kw));
    }

    @GetMapping("/{id}")
    public Result<CostContractTemplate> detail(@PathVariable Long id) {
        return Result.ok(svc.detail(id));
    }

    @PostMapping
    public Result<CostContractTemplate> upload(@RequestParam("file") MultipartFile file,
                                               @RequestParam(required = false) String name,
                                               @RequestParam(required = false) String contractType,
                                               @RequestParam(required = false) String description) {
        return Result.ok(svc.upload(file, name, contractType, description));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    /** 按模板 + 合同数据生成 .docx */
    @PostMapping("/{id}/generate")
    public Result<Map<String, Object>> generate(@PathVariable Long id, @RequestParam Long contractId) {
        return Result.ok(svc.generate(id, contractId));
    }
}
