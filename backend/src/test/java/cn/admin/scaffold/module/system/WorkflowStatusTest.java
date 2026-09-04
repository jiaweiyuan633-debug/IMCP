package cn.admin.scaffold.module.system;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkflowStatusTest {

    @Test
    void enumNamesFollowDatabaseConvention() {
        assertEquals("PENDING", WorkflowStatus.PENDING.name());
        assertEquals("APPROVED", WorkflowStatus.APPROVED.name());
        assertEquals("REJECTED", WorkflowStatus.REJECTED.name());
        assertEquals("WITHDRAWN", WorkflowStatus.WITHDRAWN.name());
    }
}
