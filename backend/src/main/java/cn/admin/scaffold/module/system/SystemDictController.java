package cn.admin.scaffold.module.system;

import cn.admin.scaffold.common.PageResult;
import cn.admin.scaffold.common.Result;
import cn.admin.scaffold.common.annotation.OperLog;
import cn.admin.scaffold.module.system.dto.DictDataQuery;
import cn.admin.scaffold.module.system.dto.DictDataSaveRequest;
import cn.admin.scaffold.module.system.dto.DictTypeQuery;
import cn.admin.scaffold.module.system.dto.DictTypeSaveRequest;
import cn.admin.scaffold.module.system.vo.DictDataVo;
import cn.admin.scaffold.module.system.vo.DictTypeVo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/dict")
@RequiredArgsConstructor
public class SystemDictController {

    private final SystemDictService dictService;

    @GetMapping("/shared")
    @PreAuthorize("hasAuthority('system:dict:shared:list')")
    public Result<PageResult<DictTypeVo>> sharedTypePage(DictTypeQuery query) {
        return Result.success(dictService.sharedTypePage(query));
    }

    @PostMapping("/shared")
    @PreAuthorize("hasAuthority('system:dict:shared:add')")
    @OperLog(module = "共享字典", action = "新增共享字典")
    public Result<Long> sharedTypeCreate(@Valid @RequestBody DictTypeSaveRequest request) {
        return Result.success(dictService.sharedTypeCreate(request));
    }

    @PutMapping("/shared")
    @PreAuthorize("hasAuthority('system:dict:shared:edit')")
    @OperLog(module = "共享字典", action = "编辑共享字典")
    public Result<Void> sharedTypeUpdate(@Valid @RequestBody DictTypeSaveRequest request) {
        dictService.sharedTypeUpdate(request);
        return Result.success();
    }

    @GetMapping("/type")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public Result<PageResult<DictTypeVo>> typePage(DictTypeQuery query) {
        return Result.success(dictService.typePage(query));
    }

    @PostMapping("/type")
    @PreAuthorize("hasAuthority('system:dict:add')")
    @OperLog(module = "字典管理", action = "新增字典类型")
    public Result<Long> typeCreate(@Valid @RequestBody DictTypeSaveRequest request) {
        return Result.success(dictService.typeCreate(request));
    }

    @PutMapping("/type")
    @PreAuthorize("hasAuthority('system:dict:edit')")
    @OperLog(module = "字典管理", action = "编辑字典类型")
    public Result<Void> typeUpdate(@Valid @RequestBody DictTypeSaveRequest request) {
        dictService.typeUpdate(request);
        return Result.success();
    }

    @DeleteMapping("/type/{id}")
    @PreAuthorize("hasAuthority('system:dict:delete')")
    @OperLog(module = "字典管理", action = "删除字典类型")
    public Result<Void> typeDelete(@PathVariable Long id) {
        dictService.typeDelete(id);
        return Result.success();
    }

    @GetMapping("/data")
    @PreAuthorize("hasAuthority('system:dict:list')")
    public Result<PageResult<DictDataVo>> dataPage(DictDataQuery query) {
        return Result.success(dictService.dataPage(query));
    }

    @GetMapping("/data/type/{dictType}")
    public Result<List<DictDataVo>> dataByType(@PathVariable String dictType) {
        return Result.success(dictService.dataByType(dictType));
    }

    @PostMapping("/data")
    @PreAuthorize("hasAuthority('system:dict:data:add')")
    @OperLog(module = "字典管理", action = "新增字典数据")
    public Result<Long> dataCreate(@Valid @RequestBody DictDataSaveRequest request) {
        return Result.success(dictService.dataCreate(request));
    }

    @PutMapping("/data")
    @PreAuthorize("hasAuthority('system:dict:data:edit')")
    @OperLog(module = "字典管理", action = "编辑字典数据")
    public Result<Void> dataUpdate(@Valid @RequestBody DictDataSaveRequest request) {
        dictService.dataUpdate(request);
        return Result.success();
    }

    @DeleteMapping("/data/{id}")
    @PreAuthorize("hasAuthority('system:dict:data:delete')")
    @OperLog(module = "字典管理", action = "删除字典数据")
    public Result<Void> dataDelete(@PathVariable Long id) {
        dictService.dataDelete(id);
        return Result.success();
    }
}

