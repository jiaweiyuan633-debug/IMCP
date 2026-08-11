-- 批次4 修复 W2：补齐 V47~V50 缺失的按钮权限种子。
-- Controller @PreAuthorize 已引用这些权限编码，但 sys_menu 无对应种子 → 角色无该权限 → 403。
-- 权限来源 selectPermsByUserId = sys_menu.perm（经 sys_role_menu），故新增按钮须显式授权 role_id=1。
-- 当前最大 menu id = 165，新增 166~175。
INSERT INTO sys_menu (id, parent_id, name, type, path, component, perm, icon, sort, visible, status)
VALUES
    -- 报表定义（143 报表定义菜单下）：分页查询 + 执行报表（V47 只种了 view/add/edit/delete）
    (166, 143, '查询报表', 'button', NULL, NULL, 'report:definition:list',    NULL, 4, 0, 1),
    (167, 143, '执行报表', 'button', NULL, NULL, 'report:definition:execute', NULL, 5, 0, 1),
    -- 物模型（147 物模型菜单下）：查看详情/渲染结构（V48 只种了 list/add/edit/delete）
    (168, 147, '查看物模型', 'button', NULL, NULL, 'device:thing-model:view', NULL, 4, 0, 1),
    -- 导入导出任务（158 任务记录菜单下）：查看任务详情 + 下载导出结果（V49 只种了 list/create）
    (169, 158, '查看任务', 'button', NULL, NULL, 'importexport:job:view',     NULL, 2, 0, 1),
    (170, 158, '下载结果', 'button', NULL, NULL, 'importexport:job:download', NULL, 3, 0, 1),
    -- 表单定义（161 表单定义菜单下）：查看详情/渲染结构 + 发布（V50 只种了 list/add/edit/delete）
    (171, 161, '查看表单', 'button', NULL, NULL, 'form:definition:view',    NULL, 4, 0, 1),
    (172, 161, '发布表单', 'button', NULL, NULL, 'form:definition:publish', NULL, 5, 0, 1),
    -- 提交记录（165 提交记录菜单下）：提交、查看详情、审批流转（V50 只种了 list）
    (173, 165, '提交表单', 'button', NULL, NULL, 'form:instance:submit',  NULL, 1, 0, 1),
    (174, 165, '查看提交', 'button', NULL, NULL, 'form:instance:view',    NULL, 2, 0, 1),
    (175, 165, '审批提交', 'button', NULL, NULL, 'form:instance:approve', NULL, 3, 0, 1);

-- 授权给超管 role_id=1（与各模块菜单 V47~V50 原有授权一致）
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id BETWEEN 166 AND 175;
