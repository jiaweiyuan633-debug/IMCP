"""CRUD 生成器单测：模板引擎 + 字段派生 + 输出契约。

运行：cd scripts/crud-gen && python -m unittest discover -s tests -v
"""
from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(SCRIPT_DIR))

from crud_gen import SpecError, camel_to_kebab, generate, render  # noqa: E402

TEMPLATES = SCRIPT_DIR / "templates"
EXAMPLE = SCRIPT_DIR / "spec.example.json"


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
            "backend/src/main/java/com/example/admin/module/device/entity/AlarmRuleDO.java",
            "backend/src/main/java/com/example/admin/module/device/mapper/AlarmRuleMapper.java",
            "backend/src/main/java/com/example/admin/module/device/dto/AlarmRuleQuery.java",
            "backend/src/main/java/com/example/admin/module/device/dto/AlarmRuleSaveRequest.java",
            "backend/src/main/java/com/example/admin/module/device/vo/AlarmRuleVo.java",
            "backend/src/main/java/com/example/admin/module/device/AlarmRuleService.java",
            "backend/src/main/java/com/example/admin/module/device/AlarmRuleController.java",
            "frontend/src/api/device.ts",
            "frontend/src/views/device/alarm-rule/index.vue",
        ]
        for p in expected_prefixes:
            self.assertIn(p, self.files, "缺少生成文件：%s" % p)

    def test_service_has_no_dangling_gt(self):
        service = self.files["backend/src/main/java/com/example/admin/module/device/AlarmRuleService.java"]
        for line in service.splitlines():
            # DO 不会出现在嵌套泛型里，DO>> 即为笔误（AlarmRuleVo>> 在 Result<PageResult<Vo>> 中合法，故只查 DO）
            self.assertNotRegex(line, r"DO>>", "Service 残留多余 >：%s" % line)

    def test_controller_has_no_dangling_gt(self):
        controller = self.files["backend/src/main/java/com/example/admin/module/device/AlarmRuleController.java"]
        self.assertIn("Result<AlarmRuleVo> getById", controller)
        self.assertNotIn("DO>>", controller)

    def test_do_contains_table_annotation_and_fields(self):
        do_content = self.files["backend/src/main/java/com/example/admin/module/device/entity/AlarmRuleDO.java"]
        self.assertIn('@TableName("device_alarm_rule")', do_content)
        self.assertIn("private String name;", do_content)
        self.assertIn("private BigDecimal threshold;", do_content)
        self.assertIn("import java.math.BigDecimal;", do_content)
        self.assertIn("@Version", do_content)
        self.assertIn("@TableLogic", do_content)
        # 业务字段带注释
        self.assertIn("/** 规则名称 */", do_content)

    def test_request_validation_annotations(self):
        request = self.files["backend/src/main/java/com/example/admin/module/device/dto/AlarmRuleSaveRequest.java"]
        self.assertIn('@NotBlank(message = "规则名称不能为空")', request)
        self.assertIn('@NotNull(message = "级别 0INFO 1WARN 2CRITICAL不能为空")', request)
        self.assertIn('@Size(max = 50, message = "规则名称长度不能超过 50")', request)

    def test_service_where_conditions(self):
        service = self.files["backend/src/main/java/com/example/admin/module/device/AlarmRuleService.java"]
        self.assertIn(".like(StringUtils.hasText(query.getName()), AlarmRuleDO::getName, query.getName())", service)
        self.assertIn(".eq(query.getThreshold() != null, AlarmRuleDO::getThreshold, query.getThreshold())", service)

    def test_service_perm_annotations(self):
        controller = self.files["backend/src/main/java/com/example/admin/module/device/AlarmRuleController.java"]
        self.assertIn("hasAuthority('device:alarm-rule:list')", controller)
        self.assertIn("hasAuthority('device:alarm-rule:add')", controller)
        self.assertIn("@OperLog(module = \"设备告警规则\", action = \"新增设备告警规则\")", controller)

    def test_api_ts_uses_template_literal_for_delete(self):
        api = self.files["frontend/src/api/device.ts"]
        self.assertIn("request.delete(`/device/alarm-rule/${id}`)", api)
        self.assertIn("request.get('/device/alarm-rule', { params })", api)
        self.assertIn("webhook?: string", api)   # 非 required 字段带 ?
        self.assertIn("name: string", api)       # required 字段不带 ?

    def test_view_contains_search_and_table(self):
        view = self.files["frontend/src/views/device/alarm-rule/index.vue"]
        self.assertIn("设备告警规则管理", view)
        self.assertIn("getAlarmRulePage", view)
        self.assertIn("dateColumn('createdAt'", view)
        # 中文直写、不依赖 i18n
        self.assertNotIn("useI18n", view)

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
