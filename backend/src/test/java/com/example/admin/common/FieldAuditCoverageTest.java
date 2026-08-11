package com.example.admin.common;

import com.example.admin.common.annotation.FieldAudit;
import com.example.admin.module.device.DeviceService;
import com.example.admin.module.device.dto.DeviceSaveRequest;
import com.example.admin.module.device.entity.DeviceDO;
import com.example.admin.module.system.SystemConfigService;
import com.example.admin.module.system.SystemDeptService;
import com.example.admin.module.system.SystemDictService;
import com.example.admin.module.system.SystemNoticeService;
import com.example.admin.module.system.SystemPostService;
import com.example.admin.module.system.SystemUserService;
import com.example.admin.module.system.dto.ConfigSaveRequest;
import com.example.admin.module.system.dto.DeptSaveRequest;
import com.example.admin.module.system.dto.DictDataSaveRequest;
import com.example.admin.module.system.dto.DictTypeSaveRequest;
import com.example.admin.module.system.dto.PostSaveRequest;
import com.example.admin.module.system.dto.UserSaveRequest;
import com.example.admin.module.system.entity.SysConfigDO;
import com.example.admin.module.system.entity.SysDeptDO;
import com.example.admin.module.system.entity.SysDictDataDO;
import com.example.admin.module.system.entity.SysDictTypeDO;
import com.example.admin.module.system.entity.SysNoticeDO;
import com.example.admin.module.system.entity.SysPostDO;
import com.example.admin.module.system.entity.SysUserDO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 字段级审计铺开守卫：核心基础数据实体的 update 方法必须带 @FieldAudit，
 * 防止新增服务/方法时漏接审计链路（角色已在 SystemRoleService 覆盖）。
 */
class FieldAuditCoverageTest {

    @Test
    void userUpdateIsAudited() {
        assertAudited(SystemUserService.class, "update", UserSaveRequest.class, SysUserDO.class);
    }

    @Test
    void deptUpdateIsAudited() {
        assertAudited(SystemDeptService.class, "update", DeptSaveRequest.class, SysDeptDO.class);
    }

    @Test
    void postUpdateIsAudited() {
        assertAudited(SystemPostService.class, "update", PostSaveRequest.class, SysPostDO.class);
    }

    @Test
    void configUpdateIsAudited() {
        assertAudited(SystemConfigService.class, "update", ConfigSaveRequest.class, SysConfigDO.class);
    }

    @Test
    void dictTypeUpdateIsAudited() {
        assertAudited(SystemDictService.class, "typeUpdate", DictTypeSaveRequest.class, SysDictTypeDO.class);
    }

    @Test
    void dictDataUpdateIsAudited() {
        assertAudited(SystemDictService.class, "dataUpdate", DictDataSaveRequest.class, SysDictDataDO.class);
    }

    @Test
    void deviceUpdateIsAudited() {
        assertAudited(DeviceService.class, "update", DeviceSaveRequest.class, DeviceDO.class);
    }

    @Test
    void noticeUpdateIsAudited() {
        assertAudited(SystemNoticeService.class, "update", SysNoticeDO.class, SysNoticeDO.class);
    }

    private void assertAudited(Class<?> service, String methodName, Class<?> param, Class<?> entity) {
        try {
            Method method = service.getMethod(methodName, param);
            FieldAudit audit = method.getAnnotation(FieldAudit.class);
            assertThat(audit).as("%s.%s 缺少 @FieldAudit", service.getSimpleName(), methodName).isNotNull();
            assertThat(audit.entity()).isEqualTo(entity);
            assertThat(audit.action()).isEqualTo("UPDATE");
            assertThat(audit.module()).isNotBlank();
        } catch (NoSuchMethodException e) {
            throw new AssertionError("方法不存在: " + service.getSimpleName() + "#" + methodName, e);
        }
    }
}
