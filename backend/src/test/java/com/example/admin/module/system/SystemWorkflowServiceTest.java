package com.example.admin.module.system;

import com.example.admin.module.system.entity.SysProcessNodeDO;
import com.example.admin.module.system.entity.SysWorkflowDO;
import com.example.admin.module.system.entity.SysWorkflowLogDO;
import com.example.admin.module.system.mapper.SysProcessDefMapper;
import com.example.admin.module.system.mapper.SysProcessNodeMapper;
import com.example.admin.module.system.mapper.SysUserMapper;
import com.example.admin.module.system.mapper.SysUserRoleMapper;
import com.example.admin.module.system.mapper.SysWorkflowLogMapper;
import com.example.admin.module.system.mapper.SysWorkflowMapper;
import com.example.admin.security.LoginUser;
import com.example.admin.security.SecurityUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemWorkflowServiceTest {

    @Mock
    private SysWorkflowMapper workflowMapper;

    @Mock
    private SysWorkflowLogMapper workflowLogMapper;

    @Mock
    private SysProcessDefMapper processDefMapper;

    @Mock
    private SysProcessNodeMapper processNodeMapper;

    @Mock
    private SysUserRoleMapper userRoleMapper;

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SystemNoticeService noticeService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SystemWorkflowService workflowService;

    @Test
    void approveAdvancesToNextNode() {
        SysWorkflowDO workflow = new SysWorkflowDO();
        workflow.setId(1L);
        workflow.setProcessDefId(5L);
        workflow.setStatus(WorkflowStatus.PENDING.name());
        workflow.setCurrentNodeIds("1");
        when(workflowMapper.selectById(1L)).thenReturn(workflow);

        SysProcessNodeDO first = node(1L, 1, "初审");
        SysProcessNodeDO second = node(2L, 2, "终审");
        when(processNodeMapper.selectById(1L)).thenReturn(first);
        when(processNodeMapper.selectById(2L)).thenReturn(second);
        when(processNodeMapper.selectList(any())).thenReturn(List.of(first, second));

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getLoginUser).thenReturn(admin());
            workflowService.approve(1L, 1L, "同意");
        }

        ArgumentCaptor<SysWorkflowDO> captor = ArgumentCaptor.forClass(SysWorkflowDO.class);
        verify(workflowMapper).updateById(captor.capture());
        assertEquals("2", captor.getValue().getCurrentNodeIds());
        assertEquals("终审", captor.getValue().getCurrentNodeName());
        verify(workflowLogMapper, times(2)).insert(any(SysWorkflowLogDO.class));
    }

    @Test
    void rejectFinishesWorkflow() {
        SysWorkflowDO workflow = new SysWorkflowDO();
        workflow.setId(1L);
        workflow.setStatus(WorkflowStatus.PENDING.name());
        workflow.setCurrentNodeIds("1");
        when(workflowMapper.selectById(1L)).thenReturn(workflow);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getLoginUser).thenReturn(admin());
            workflowService.reject(1L, "不同意");
        }

        ArgumentCaptor<SysWorkflowDO> captor = ArgumentCaptor.forClass(SysWorkflowDO.class);
        verify(workflowMapper).updateById(captor.capture());
        assertEquals(WorkflowStatus.REJECTED.name(), captor.getValue().getStatus());
        assertNull(captor.getValue().getCurrentNodeIds());
    }

    private LoginUser admin() {
        return LoginUser.builder()
                .userId(1L)
                .username("admin")
                .roles(List.of("admin"))
                .build();
    }

    private SysProcessNodeDO node(Long id, int order, String name) {
        SysProcessNodeDO node = new SysProcessNodeDO();
        node.setId(id);
        node.setNodeOrder(order);
        node.setNodeName(name);
        return node;
    }
}
