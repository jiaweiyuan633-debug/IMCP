package com.example.admin.module.system.warmflow;

import com.example.admin.common.BusinessException;
import com.example.admin.common.PageResult;
import com.example.admin.common.ResultCode;
import com.example.admin.common.TenantContext;
import com.example.admin.module.system.dto.ProcessDefSaveRequest;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.warm.flow.core.FlowEngine;
import org.dromara.warm.flow.core.dto.DefJson;
import org.dromara.warm.flow.core.dto.NodeJson;
import org.dromara.warm.flow.core.dto.SkipJson;
import org.dromara.warm.flow.core.entity.Definition;
import org.dromara.warm.flow.core.entity.Node;
import org.dromara.warm.flow.core.entity.Skip;
import org.dromara.warm.flow.core.enums.NodeType;
import org.dromara.warm.flow.core.enums.SkipType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarmFlowProcessDefService {

    private static final int DEFAULT_TIMEOUT_HOURS = 48;
    private static final String ROLE_PREFIX = "role:";
    private static final String ALL_PERMISSION = "all";
    private static final String CLASSICS_MODEL = "CLASSICS";
    private static final String APPROVE_NODE = "APPROVE";
    private static final String CONDITION_NODE = "CONDITION";

    private final ObjectMapper objectMapper;

    public PageResult<WarmFlowProcessDefVO> page(long pageNum, long pageSize, String defName, Integer status) {
        List<WarmFlowProcessDefVO> all = FlowEngine.defService().list(FlowEngine.newDef()).stream()
                .map(this::toVO)
                .filter(def -> !StringUtils.hasText(defName) || contains(def.getDefName(), defName))
                .filter(def -> !StringUtils.hasText(defName) || contains(def.getDefKey(), defName))
                .filter(def -> status == null || status.equals(def.getStatus()))
                .sorted(Comparator.comparing(WarmFlowProcessDefVO::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        return pageOf(all, pageNum, pageSize);
    }

    public List<WarmFlowProcessDefVO> listOptions() {
        return FlowEngine.defService().list(FlowEngine.newDef().setIsPublish(1)).stream()
                .map(this::toVO)
                .sorted(Comparator.comparing(WarmFlowProcessDefVO::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<WarmFlowProcessNodeVO> nodes(Long defId) {
        Definition definition = FlowEngine.defService().getAllDataDefinition(defId);
        if (definition == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
        List<WarmFlowProcessNodeVO> result = new ArrayList<>();
        List<Node> nodes = definition.getNodeList() == null ? List.of() : definition.getNodeList();
        int order = 0;
        for (Node node : nodes) {
            if (!isBusinessNode(node.getNodeType())) {
                continue;
            }
            result.add(toNodeVO(node, order++));
        }
        return result;
    }

    @Transactional
    public Long create(ProcessDefSaveRequest request) {
        DefJson defJson = buildDefJson(request);
        defJson.setId(null);
        saveDef(defJson);
        Definition saved = FlowEngine.defService().getOne(FlowEngine.newDef()
                .setFlowCode(request.getDefKey())
                .setFlowName(request.getDefName()));
        if (saved == null) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR);
        }
        if (request.getStatus() != null && request.getStatus() == 1) {
            FlowEngine.defService().publish(saved.getId());
        }
        return saved.getId();
    }

    @Transactional
    public void update(ProcessDefSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "流程定义 ID 不能为空");
        }
        DefJson defJson = buildDefJson(request);
        defJson.setId(request.getId());
        saveDef(defJson);
        if (request.getStatus() != null && request.getStatus() == 1) {
            FlowEngine.defService().publish(request.getId());
        } else {
            FlowEngine.defService().unPublish(request.getId());
        }
    }

    @Transactional
    public void delete(Long id) {
        if (!FlowEngine.defService().removeDef(List.of(id))) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
    }

    @Transactional
    public void publish(Long id) {
        if (!FlowEngine.defService().publish(id)) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
    }

    @Transactional
    public void unPublish(Long id) {
        if (!FlowEngine.defService().unPublish(id)) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND);
        }
    }

    private DefJson buildDefJson(ProcessDefSaveRequest request) {
        DefJson defJson = new DefJson();
        defJson.setFlowCode(request.getDefKey())
                .setFlowName(request.getDefName())
                .setCategory(request.getDescription())
                .setModelValue(CLASSICS_MODEL)
                .setIsPublish(0)
                .setFormCustom("N");

        List<NodeJson> nodeList = new ArrayList<>();
        NodeJson start = buildNode(0, "start", "开始", ALL_PERMISSION, null);
        nodeList.add(start);

        List<ProcessDefSaveRequest.NodeItem> items = request.getNodes();
        for (int i = 0; i < items.size(); i++) {
            ProcessDefSaveRequest.NodeItem item = items.get(i);
            boolean condition = CONDITION_NODE.equalsIgnoreCase(item.getNodeType());
            int warmType = condition ? NodeType.SERIAL.getKey() : NodeType.BETWEEN.getKey();
            String permission = item.getApproverRoleId() == null
                    ? ALL_PERMISSION
                    : ROLE_PREFIX + item.getApproverRoleId() + "@@admin";
            NodeJson node = buildNode(warmType, item.getNodeKey(), item.getNodeName(), permission,
                    nodeExt(item.getTimeoutHours()));
            nodeList.add(node);
        }

        NodeJson end = buildNode(2, "end", "结束", null, null);
        nodeList.add(end);

        for (int i = 0; i < nodeList.size() - 1; i++) {
            NodeJson from = nodeList.get(i);
            NodeJson to = nodeList.get(i + 1);
            SkipJson skip = new SkipJson()
                    .setNowNodeCode(from.getNodeCode())
                    .setNextNodeCode(to.getNodeCode())
                    .setSkipName("通过")
                    .setSkipType(SkipType.PASS.getKey());
            if (i + 1 < nodeList.size() - 1) {
                ProcessDefSaveRequest.NodeItem item = items.get(i);
                if (StringUtils.hasText(item.getConditionExpression())) {
                    skip.setSkipCondition(item.getConditionExpression());
                }
            }
            from.getSkipList().add(skip);
        }
        defJson.setNodeList(nodeList);
        return defJson;
    }

    private void saveDef(DefJson defJson) {
        try {
            FlowEngine.defService().saveDef(defJson, false);
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), exception.getMessage());
        }
    }

    private NodeJson buildNode(int type, String code, String name, String permission, String ext) {
        NodeJson node = new NodeJson();
        node.setNodeType(type);
        node.setNodeCode(code);
        node.setNodeName(name);
        node.setPermissionFlag(permission);
        node.setCoordinate("100," + (100 + type * 40));
        node.setExt(ext);
        node.setNodeRatio("0");
        node.setFormCustom("N");
        return node;
    }

    private String nodeExt(Integer timeoutHours) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "timeoutHours", timeoutHours == null ? DEFAULT_TIMEOUT_HOURS : timeoutHours));
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private WarmFlowProcessDefVO toVO(Definition definition) {
        WarmFlowProcessDefVO vo = new WarmFlowProcessDefVO();
        vo.setId(definition.getId());
        vo.setDefName(definition.getFlowName());
        vo.setDefKey(definition.getFlowCode());
        vo.setDescription(definition.getCategory());
        vo.setStatus(definition.getIsPublish());
        vo.setVersion(definition.getVersion());
        vo.setModelValue(definition.getModelValue());
        if (definition.getCreateTime() != null) {
            vo.setCreatedAt(LocalDateTime.ofInstant(
                    definition.getCreateTime().toInstant(), ZoneId.systemDefault()));
        }
        return vo;
    }

    private WarmFlowProcessNodeVO toNodeVO(Node node, int order) {
        WarmFlowProcessNodeVO vo = new WarmFlowProcessNodeVO();
        vo.setId(node.getId());
        vo.setNodeName(node.getNodeName());
        vo.setNodeKey(node.getNodeCode());
        vo.setNodeType(NodeType.BETWEEN.getKey().equals(node.getNodeType()) ? APPROVE_NODE : CONDITION_NODE);
        vo.setNodeOrder(order);
        vo.setTimeoutHours(parseTimeout(node.getExt()));
        vo.setApproverRoleId(parseRoleId(node.getPermissionFlag()));
        if (node.getSkipList() != null) {
            node.getSkipList().stream()
                    .filter(skip -> node.getNodeCode().equals(skip.getNextNodeCode()))
                    .map(Skip::getSkipCondition)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .ifPresent(vo::setConditionExpression);
        }
        return vo;
    }

    private Integer parseTimeout(String ext) {
        if (!StringUtils.hasText(ext)) {
            return DEFAULT_TIMEOUT_HOURS;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(ext, new TypeReference<Map<String, Object>>() {
            });
            Object value = map.get("timeoutHours");
            return value instanceof Number number ? number.intValue() : DEFAULT_TIMEOUT_HOURS;
        } catch (JsonProcessingException exception) {
            return DEFAULT_TIMEOUT_HOURS;
        }
    }

    private Long parseRoleId(String permissionFlag) {
        if (!StringUtils.hasText(permissionFlag)) {
            return null;
        }
        for (String permission : permissionFlag.split("@@")) {
            if (permission.startsWith(ROLE_PREFIX)) {
                try {
                    return Long.valueOf(permission.substring(ROLE_PREFIX.length()));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private boolean isBusinessNode(Integer nodeType) {
        return NodeType.BETWEEN.getKey().equals(nodeType)
                || NodeType.SERIAL.getKey().equals(nodeType)
                || NodeType.PARALLEL.getKey().equals(nodeType);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword.toLowerCase());
    }

    private PageResult<WarmFlowProcessDefVO> pageOf(List<WarmFlowProcessDefVO> all, long pageNum, long pageSize) {
        int from = (int) Math.min((pageNum - 1) * pageSize, all.size());
        int to = (int) Math.min(from + pageSize, all.size());
        long total = all.size();
        Page<WarmFlowProcessDefVO> page = new Page<>(pageNum, pageSize, total);
        page.setRecords(all.subList(from, to));
        return PageResult.of(page, page.getRecords());
    }
}
