"""CRUD 生成器单测：模板引擎 + 字段派生 + 输出契约。

运行：cd scripts/crud-gen && python -m unittest discover -s tests -v
"""
from __future__ import annotations

import json
import re
import sys
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SCRIPT_DIR))

from crud_gen import BASE_PACKAGE, SpecError, camel_to_kebab, generate, render  # noqa: E402

TEMPLATES = SCRIPT_DIR / "templates"
EXAMPLE = SCRIPT_DIR / "spec.example.json"

# 期望路径从 crud_gen.BASE_PACKAGE 派生，避免包名迁移后单测再次失联。
# BASE_PACKAGE = "cn.admin.scaffold.module" → cn/admin/scaffold/module/device
JAVA_ROOT = "backend/src/main/java/%s/device" % BASE_PACKAGE.replace(".", "/")
DO_FILE = "%s/entity/AlarmRuleDO.java" % JAVA_ROOT
QUERY_FILE = "%s/dto/AlarmRuleQuery.java" % JAVA_ROOT
REQUEST_FILE = "%s/dto/AlarmRuleSaveRequest.java" % JAVA_ROOT
VO_FILE = "%s/vo/AlarmRuleVo.java" % JAVA_ROOT
SERVICE_FILE = "%s/AlarmRuleService.java" % JAVA_ROOT
CONTROLLER_FILE = "%s/AlarmRuleController.java" % JAVA_ROOT
MAPPER_FILE = "%s/mapper/AlarmRuleMapper.java" % JAVA_ROOT
API_FILE = "frontend/src/api/device.ts"
VIEW_FILE = "frontend/src/views/device/alarm-rule/index.vue"


def load_example() -> dict:
    return json.loads(EXAMPLE.read_text(encoding="utf-8"))


class RenderTest(unittest.TestCase):
    def test_simple_variable(self):
        self.assertEqual(render("hello {{name}}", {"name": "world"}), "hello world")

    def test_loop_expands_items(self):
        model = {"lines": ["a", "b", "c"], "title": "T"}
        out = render("{{title}}\n[[for:lines]]{{item}}\n[[/for]]", model)
        self.assertEqual(out, "T\na\nb\nc\n")

    def test_loop_body_keeps_outer_var(self):
        model = {"pkg": "com.x", "rows": ["1", "2"]}
        out = render("[[for:rows]]{{item}}/{{pkg}}\n[[/for]]", model)
        self.assertEqual(out, "1/com.x\n2/com.x\n")

    def test_unknown_var_kept_as_is(self):
        self.assertEqual(render("{{missing}}", {}), "{{missing}}")


class TransformTest(unittest.TestCase):
    def test_camel_to_kebab(self):
        self.assertEqual(camel_to_kebab("AlarmRule"), "alarm-rule")
        self.assertEqual(camel_to_kebab("SysMenu"), "sys-menu")
        self.assertEqual(camel_to_kebab("ImportExportJob"), "import-export-job")


class GenerateTest(unittest.TestCase):
    def setUp(self):
        self.files = generate(load_example(), TEMPLATES)

    def test_outputs_nine_files(self):
        self.assertEqual(len(self.files), 9)
        expected_prefixes = [
            DO_FILE,
            MAPPER_FILE,
            QUERY_FILE,
            REQUEST_FILE,
            VO_FILE,
            SERVICE_FILE,
            CONTROLLER_FILE,
            API_FILE,
            VIEW_FILE,
        ]
        for p in expected_prefixes:
            self.assertIn(p, self.files, "缺少生成文件：%s" % p)

    def test_outputs_use_new_base_package_only(self):
        """迁移后输出不再包含 com.example / com/example 残留。"""
        for rel in self.files:
            self.assertNotIn("com/example", rel, "残留旧包路径：%s" % rel)
            self.assertNotIn("com.example", self.files[rel], "残留旧包名：%s" % rel)
        self.assertIn("package %s.device.entity;" % BASE_PACKAGE, self.files[DO_FILE])

    def test_service_has_no_dangling_gt(self):
        service = self.files[SERVICE_FILE]
        for line in service.splitlines():
            # DO 不会出现在嵌套泛型里，DO>> 即为笔误（AlarmRuleVo>> 在 Result<PageResult<Vo>> 中合法，故只查 DO）
            self.assertNotRegex(line, r"DO>>", "Service 残留多余 >：%s" % line)

    def test_controller_has_no_dangling_gt(self):
        controller = self.files[CONTROLLER_FILE]
        self.assertIn("Result<AlarmRuleVo> getById", controller)
        self.assertNotIn("DO>>", controller)

    def test_do_contains_table_annotation_and_fields(self):
        do_content = self.files[DO_FILE]
        self.assertIn('@TableName("device_alarm_rule")', do_content)
        self.assertIn("private String name;", do_content)
        self.assertIn("private BigDecimal threshold;", do_content)
        self.assertIn("import java.math.BigDecimal;", do_content)
        self.assertIn("@Version", do_content)
        self.assertIn("@TableLogic", do_content)
        # 业务字段带注释
        self.assertIn("/** 规则名称 */", do_content)

    def test_request_validation_annotations(self):
        request = self.files[REQUEST_FILE]
        self.assertIn('@NotBlank(message = "规则名称不能为空")', request)
        self.assertIn('@NotNull(message = "级别 0INFO 1WARN 2CRITICAL不能为空")', request)
        self.assertIn('@Size(max = 50, message = "规则名称长度不能超过 50")', request)

    def test_service_where_conditions(self):
        service = self.files[SERVICE_FILE]
        self.assertIn(".like(StringUtils.hasText(query.getName()), AlarmRuleDO::getName, query.getName())", service)
        self.assertIn(".eq(query.getThreshold() != null, AlarmRuleDO::getThreshold, query.getThreshold())", service)

    def test_service_perm_annotations(self):
        controller = self.files[CONTROLLER_FILE]
        self.assertIn("hasAuthority('device:alarm-rule:list')", controller)
        self.assertIn("hasAuthority('device:alarm-rule:add')", controller)
        self.assertIn("@OperLog(module = \"设备告警规则\", action = \"新增设备告警规则\")", controller)

    def test_api_ts_uses_template_literal_for_delete(self):
        api = self.files[API_FILE]
        self.assertIn("request.delete(`/device/alarm-rule/${id}`)", api)
        self.assertIn("request.get('/device/alarm-rule', { params })", api)
        self.assertIn("request.get(`/device/alarm-rule/${id}`)", api)  # 详情 GET /{id} 对齐
        self.assertIn("webhook?: string", api)   # 非 required 字段带 ?
        self.assertIn("name: string", api)       # required 字段不带 ?

    def test_api_ts_contract_matches_controller_mappings(self):
        """契约断言：api.ts 的每个 URL 都能在后端 Controller 找到逐字一致的映射。

        前端 api.ts 不含 /api 前缀（axios baseURL 已含 /api），{id} 用模板字面量 ${id}，
        因此断言时把前端路径补上 /api 并把 ${id} 归一为 {id} 再与 Controller 映射比对。
        """
        api = self.files[API_FILE]
        controller = self.files[CONTROLLER_FILE]

        # 1) 提取 Controller 的全部 (HTTP 动词, 完整路径) 映射
        base_m = re.search(r'@RequestMapping\("(/api/[^"]+)"\)', controller)
        self.assertIsNotNone(base_m, "Controller 缺少 @RequestMapping 基路径")
        base = base_m.group(1)  # /api/device/alarm-rule
        mappings = set()
        for verb, sub in re.findall(r"@(Get|Post|Put|Delete)Mapping(?:\(\s*\"([^\"]*)\"\s*\))?", controller):
            mappings.add((verb.upper(), base + (sub or "")))

        # 2) 列表 GET 落在基路径（base 风格），不得残留 /page
        self.assertIn(("GET", base), mappings)
        self.assertNotIn(("GET", base + "/page"), mappings)
        self.assertIn("@PreAuthorize(\"hasAuthority('device:alarm-rule:list')\")", controller)

        # 3) 提取 api.ts 的 request.<verb> 调用路径
        api_calls = set()
        for m in re.finditer(r"request\.(get|post|put|delete)\(\s*([`'\"])([^`'\"]+)\2", api):
            verb, _, path = m.group(1), m.group(2), m.group(3)
            if "params" in path or "data" in path:
                continue
            full = "/api" + path.replace("${id}", "{id}") if not path.startswith("/api") else path
            api_calls.add((verb.upper(), full.replace("${id}", "{id}")))

        # 4) api.ts 的调用必须全部能在 Controller 映射中找到（无 404/405 契约缺口）
        for call in api_calls:
            self.assertIn(call, mappings, "api.ts 调用 %s 在后端无对应映射" % (call,))

    def test_view_contains_search_and_table(self):
        view = self.files[VIEW_FILE]
        self.assertIn("设备告警规则管理", view)
        self.assertIn("getAlarmRulePage", view)
        self.assertIn("dateColumn('createdAt'", view)
        # 中文直写、不依赖 i18n
        self.assertNotIn("useI18n", view)

    def test_view_build_params_render_from_search_fields(self):
        """index.vue 的 buildParams 必须把搜索字段逐项带上（param_fields 渲染修复）。"""
        view = self.files[VIEW_FILE]
        for line in ("name: (query.name as string) || undefined,",
                     "threshold: query.threshold as number | undefined,",
                     "severity: query.severity as number | undefined,"):
            self.assertIn(line, view, "buildParams 缺少参数行：%s" % line)
        self.assertIn("request.get('/device/alarm-rule', { params })", self.files[API_FILE])

    def test_no_unrendered_placeholders(self):
        for rel, content in self.files.items():
            self.assertNotRegex(content, r"\{\{\w+\}\}", "未渲染占位符：%s" % rel)
            self.assertNotRegex(content, r"\[\[for:", "未展开循环：%s" % rel)

    def test_no_blank_line_stray_indent_in_java(self):
        for rel in self.files:
            if rel.endswith(".java"):
                content = self.files[rel]
                for i, line in enumerate(content.splitlines(), 1):
                    self.assertNotRegex(line, r"^\s*\{\{", "残留模板行：%s:%s" % (rel, i))


class DataScopeGenerationTest(unittest.TestCase):
    """spec.datascope=true 时生成 @DataScope 注解、import 与租户上下文注入。"""

    def setUp(self):
        spec = load_example()
        spec["datascope"] = True
        self.service = generate(spec, TEMPLATES)[SERVICE_FILE]

    def test_injects_data_scope_annotation_and_import(self):
        # 单行模式 ^ 只匹配整体开头，断言用无锚定正则（行内查找注解行内容）
        self.assertRegex(self.service, r"@DataScope\(tables\s*=\s*\{\s*\"device_alarm_rule\"\s*\}\)",
                         "应生成 @DataScope 注解行")
        self.assertIn("import cn.admin.scaffold.common.annotation.DataScope;", self.service)

    def test_injects_tenant_context_on_create(self):
        self.assertIn("entity.setTenantId(TenantContext.getTenantId());", self.service)

    def test_datascope_false_yields_no_annotation(self):
        spec = load_example()  # 默认无 datascope
        service = generate(spec, TEMPLATES)[SERVICE_FILE]
        # 注释文本可能提及 @DataScope，断言实际注解行与 import 不存在即可
        self.assertNotRegex(service, r"^\s*@DataScope\(", "不应生成 @DataScope 注解")
        self.assertNotIn("import cn.admin.scaffold.common.annotation.DataScope;", service)
        self.assertNotIn("TenantContext.getTenantId()", service)


class SpecValidationTest(unittest.TestCase):
    def test_missing_required_key_raises(self):
        with self.assertRaises(SpecError):
            generate({"module": "x", "entity": "Foo", "table": "t"}, TEMPLATES)

    def test_unknown_type_raises(self):
        spec = load_example()
        spec["fields"][0]["type"] = "DateTime"
        with self.assertRaises(SpecError):
            generate(spec, TEMPLATES)

    def test_invalid_entity_raises(self):
        spec = load_example()
        spec["entity"] = "alarmRule"
        with self.assertRaises(SpecError):
            generate(spec, TEMPLATES)

    def test_empty_fields_raises(self):
        spec = load_example()
        spec["fields"] = []
        with self.assertRaises(SpecError):
            generate(spec, TEMPLATES)


if __name__ == "__main__":
    unittest.main()
