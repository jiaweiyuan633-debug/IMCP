package cn.admin.scaffold.common;

import cn.admin.scaffold.common.annotation.FieldAudit;
import cn.admin.scaffold.module.device.DeviceService;
import cn.admin.scaffold.module.device.dto.DeviceSaveRequest;
import cn.admin.scaffold.module.device.entity.DeviceDO;
import cn.admin.scaffold.module.system.SystemConfigService;
import cn.admin.scaffold.module.system.SystemDeptService;
import cn.admin.scaffold.module.system.SystemDictService;
import cn.admin.scaffold.module.system.SystemNoticeService;
import cn.admin.scaffold.module.system.SystemPostService;
import cn.admin.scaffold.module.system.SystemUserService;
import cn.admin.scaffold.module.system.dto.ConfigSaveRequest;
import cn.admin.scaffold.module.system.dto.DeptSaveRequest;
import cn.admin.scaffold.module.system.dto.DictDataSaveRequest;
import cn.admin.scaffold.module.system.dto.DictTypeSaveRequest;
import cn.admin.scaffold.module.system.dto.PostSaveRequest;
import cn.admin.scaffold.module.system.dto.UserSaveRequest;
import cn.admin.scaffold.module.system.entity.SysConfigDO;
import cn.admin.scaffold.module.system.entity.SysDeptDO;
import cn.admin.scaffold.module.system.entity.SysDictDataDO;
import cn.admin.scaffold.module.system.entity.SysDictTypeDO;
import cn.admin.scaffold.module.system.entity.SysNoticeDO;
import cn.admin.scaffold.module.system.entity.SysPostDO;
import cn.admin.scaffold.module.system.entity.SysUserDO;
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
