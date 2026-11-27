package com.reip.mdm.controller;

import com.reip.common.result.Result;
import com.reip.mdm.entity.MdmBuilding;
import com.reip.mdm.service.MdmProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 楼栋（独立列表入口，维护入口见 /api/mdm/projects/{id}/buildings）
 */
@RestController
@RequestMapping("/api/mdm/buildings")
@RequiredArgsConstructor
public class MdmBuildingController {

    private final MdmProjectService svc;

    /** 楼栋列表（按项目过滤） */
    @GetMapping
    public Result<List<MdmBuilding>> list(@RequestParam(required = false) Long projectId) {
        return Result.ok(svc.buildingList(projectId));
    }
}
