package com.example.admin.module.system.warmflow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.admin.common.TenantContext;
import com.example.admin.module.system.entity.SysProcessDefDO;
import com.example.admin.module.system.entity.SysProcessNodeDO;
import com.example.admin.module.system.mapper.SysProcessDefMapper;
import com.example.admin.module.system.mapper.SysProcessNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.NodeJson;
import org.dromara.warm.flow.core.dto.SkipJson;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WarmFlowLegacyMigrator implements ApplicationRunner {

    private static final String ROLE_PREFIX = "role:";
    private static final String ALL_PERMISSION = "all";
    private static final String CLASSICS_MODEL = "CLASSICS";

    private final SysProcessDefMapper processDefMapper;
    private final SysProcessNodeMapper processNodeMapper;

    @Override
    public void run(ApplicationArguments args) {
        List<SysProcessDefDO> legacyDefs = processDefMapper.selectList(null);
        for (SysProcessDefDO def : legacyDefs) {
            Long tenantId = def.getTenantId() == null ? 1L : def.getTenantId();
            TenantContext.setTenantId(tenantId);
            try {
                List<Definition> existing = FlowEngine.defService().getByFlowCode(def.getDefKey());
                if (!existing.isEmpty()) {
                    existing.forEach(item -> ensureAdminPermission(item.getId()));
                    continue;
                }
                List<SysProcessNodeDO> nodes = processNodeMapper.selectList(
                        new LambdaQueryWrapper<SysProcessNodeDO>()
                                .eq(SysProcessNodeDO::getProcessDefId, def.getId())
                                .orderByAsc(SysProcessNodeDO::getNodeOrder));
                if (nodes.isEmpty()) {
                    continue;
                }
                DefJson defJson = buildDefJson(def, nodes);
                FlowEngine.defService().saveDef(defJson, false);
                var saved = FlowEngine.defService().getOne(FlowEngine.newDef().setFlowCode(def.getDefKey()));
                if (saved != null && def.getStatus() != null && def.getStatus() == 1) {
                    FlowEngine.defService().publish(saved.getId());
                }
                if (saved != null) {
                    ensureAdminPermission(saved.getId());
                }
                log.info("Migrated legacy workflow definition {} to Warm-Flow", def.getDefKey());
            } catch (Exception exception) {
                log.warn("Skip legacy workflow definition {}: {}", def.getDefKey(), exception.getMessage());
            }
        }
    }

    private DefJson buildDefJson(SysProcessDefDO def, List<SysProcessNodeDO> nodes) {
        DefJson defJson = new DefJson();
        defJson.setFlowCode(def.getDefKey())
                .setFlowName(def.getDefName())
                .setCategory(def.getDescription())
                .setModelValue(CLASSICS_MODEL)
                .setIsPublish(0)
                .setFormCustom("N");

        List<NodeJson> nodeList = new ArrayList<>();
        NodeJson start = new NodeJson()
                .setNodeType(NodeType.START.getKey())
                .setNodeCode("start")
                .setNodeName("开始")
                .setPermissionFlag(ALL_PERMISSION)
                .setNodeRatio("0")
                .setFormCustom("N");
        nodeList.add(start);

        for (SysProcessNodeDO node : nodes) {
            boolean condition = "CONDITION".equalsIgnoreCase(node.getNodeType());
            String permission = node.getApproverRoleId() == null
                    ? ALL_PERMISSION
                    : ROLE_PREFIX + node.getApproverRoleId() + "@@admin";
            nodeList.add(new NodeJson()
                    .setNodeType(condition ? NodeType.SERIAL.getKey() : NodeType.BETWEEN.getKey())
                    .setNodeCode(node.getNodeKey())
                    .setNodeName(node.getNodeName())
                    .setPermissionFlag(permission)
                    .setNodeRatio("0")
                    .setFormCustom("N"));
        }

        NodeJson end = new NodeJson()
                .setNodeType(NodeType.END.getKey())
                .setNodeCode("end")
                .setNodeName("结束")
                .setFormCustom("N");
        nodeList.add(end);

        for (int i = 0; i < nodeList.size() - 1; i++) {
            NodeJson from = nodeList.get(i);
            NodeJson to = nodeList.get(i + 1);
            SkipJson skip = new SkipJson()
                    .setNowNodeCode(from.getNodeCode())
                    .setNextNodeCode(to.getNodeCode())
                    .setSkipName("通过")
                    .setSkipType(SkipType.PASS.getKey());
            if (i + 1 < nodeList.size() - 1 && StringUtils.hasText(nodes.get(i).getConditionExpression())) {
                skip.setSkipCondition(nodes.get(i).getConditionExpression());
            }
            from.getSkipList().add(skip);
        }
        defJson.setNodeList(nodeList);
        return defJson;
    }

    private void ensureAdminPermission(Long definitionId) {
        List<Node> nodes = FlowEngine.nodeService().getByDefId(definitionId);
        for (Node node : nodes) {
            String permission = node.getPermissionFlag();
            if (permission != null && permission.contains(ROLE_PREFIX)
                    && !permission.contains("@@admin")) {
                node.setPermissionFlag(permission + "@@admin");
                FlowEngine.nodeService().updateById(node);
            }
        }
    }
}
