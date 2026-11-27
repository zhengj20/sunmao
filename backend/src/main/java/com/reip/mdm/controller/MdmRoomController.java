package com.reip.mdm.controller;

import com.reip.common.result.PageResult;
import com.reip.common.result.Result;
import com.reip.mdm.entity.MdmRoom;
import com.reip.mdm.service.MdmRoomBoardService;
import com.reip.mdm.service.MdmRoomService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 房间（生成器/分页/维护/楼栋看板/合并）
 */
@RestController
@RequestMapping("/api/mdm/rooms")
@RequiredArgsConstructor
public class MdmRoomController {

    private final MdmRoomService svc;
    private final MdmRoomBoardService board;

    /** 楼栋汇总（按项目：每栋房间数/面积/可售/已售/货值） */
    @GetMapping("/summary")
    public Result<List<Map<String, Object>>> summary(@RequestParam Long projectId) {
        return Result.ok(board.buildingSummary(projectId));
    }

    /** 楼栋房间明细（可视化看板数据源，含表价/底价/销控状态） */
    @GetMapping("/building/{buildingId}")
    public Result<List<Map<String, Object>>> building(@PathVariable Long buildingId) {
        return Result.ok(board.buildingRooms(buildingId));
    }

    /** 合并房间（同楼栋/楼层/单元且均可售；面积与表价/底价汇总到目标房） */
    @PostMapping("/merge")
    public Result<Map<String, Object>> merge(@RequestBody MergeForm form) {
        return Result.ok(board.merge(form.getRoomIds()));
    }

    /** 房间分页（可按楼栋/项目/销控状态过滤） */
    @GetMapping("/page")
    public Result<PageResult<MdmRoom>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "20") long size,
                                            @RequestParam(required = false) Long buildingId,
                                            @RequestParam(required = false) Long projectId,
                                            @RequestParam(required = false) String saleStatus,
                                            @RequestParam(required = false) String kw) {
        return Result.ok(svc.page(current, size, projectId, buildingId, saleStatus, kw));
    }

    /** 房间批量生成：{buildingId, startFloor, endFloor, units, roomsPerFloor} */
    @PostMapping("/generate")
    public Result<Map<String, Integer>> generate(@RequestBody MdmRoomService.GenerateForm form) {
        return Result.ok(svc.generate(form));
    }

    /** 房间修改（非 INIT 状态禁止修改户型/面积等属性） */
    @PutMapping
    public Result<Void> update(@RequestBody MdmRoom room) {
        svc.update(room);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return Result.ok();
    }

    @Data
    public static class MergeForm {
        private List<Long> roomIds;
    }
}
