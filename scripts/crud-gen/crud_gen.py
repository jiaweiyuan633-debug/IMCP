#!/usr/bin/env python3
"""零依赖 CRUD 代码生成器（R4-1.36，批次9）。

根据一个 JSON 规格文件（表 + 字段清单），渲染 MyBatis-Plus 标准 CRUD 全套代码：
  - 后端：DO / Mapper / Query / SaveRequest / Vo / Service / Controller
  - 前端：src/api/{module}.ts + src/views/{module}/{kebab}/index.vue

设计要点：
  - 仅用 Python 标准库，不引入任何第三方依赖（可离线运行、可单测）。
  - 模板引擎极简：{{VAR}} 变量替换 + [[for:list]] 循环（循环体内用 {{item}} 引用预展开行）。
  - 输出遵循 docs/architecture-conventions.md 分层规约与现有模块代码风格。
  - 生成代码是「标准 CRUD 骨架」：分页/详情/新增/编辑/删除，含 @PreAuthorize、@OperLog、
    @Valid、MyBatis-Plus 逻辑删除/乐观锁/审计/租户字段骨架；复杂业务在生成后手写扩展。

用法：
  python scripts/crud-gen/crud_gen.py path/to/spec.json [--out ROOT] [--dry-run]

示例规格见 spec.example.json。
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
PROJECT_ROOT = SCRIPT_DIR.parent.parent

BASE_PACKAGE = "com.example.admin.module"

# Java 类型 → TypeScript 类型
TS_TYPE_MAP = {
    "String": "string",
    "Long": "number",
    "Integer": "number",
    "BigDecimal": "number",
    "LocalDateTime": "string",
    "LocalDate": "string",
    "Boolean": "boolean",
}

# 需要额外 import 的 Java 类型
JAVA_IMPORT_MAP = {
    "BigDecimal": "java.math.BigDecimal",
    "LocalDateTime": "java.time.LocalDateTime",
    "LocalDate": "java.time.LocalDate",
}

VAR_RE = re.compile(r"\{\{(\w+)\}\}")
FOR_RE = re.compile(r"\[\[for:(\w+)\]\](.*?)\[\[/for\]\]", re.S)


class SpecError(Exception):
    """规格文件校验失败。"""


def render(template: str, model: dict) -> str:
    """渲染模板：先展开 [[for:list]] 循环，再做 {{VAR}} 变量替换。"""

    def expand_for(m: re.Match) -> str:
        name, body = m.group(1), m.group(2)
        parts = []
        for item in model.get(name, []):
            parts.append(VAR_RE.sub(
                lambda mm: str(item) if mm.group(1) == "item" else "{{%s}}" % mm.group(1),
                body))
        return "".join(parts)

    out = FOR_RE.sub(expand_for, template)
    return VAR_RE.sub(lambda m: str(model.get(m.group(1), "{{%s}}" % m.group(1))), out)


def _upper0(name: str) -> str:
    return name[0].upper() + name[1:] if name else name


def camel_to_kebab(name: str) -> str:
    """AlarmRule -> alarm-rule；SysMenu -> sys-menu。"""
    return re.sub(r"(?<!^)(?=[A-Z])", "-", name).lower()


class Field:
    """单字段描述：类型映射、查询/校验派生行文本由 build_model 统一生成。"""

    def __init__(self, raw: dict, index: int):
        self.name = raw["name"]
        self.type = raw["type"]
        self.comment = raw.get("comment", self.name)
        self.query_like = bool(raw.get("queryLike"))
        self.query_eq = bool(raw.get("queryEq"))
        self.required = bool(raw.get("required"))
        self.max = raw.get("max")
        self.index = index

        if self.type not in TS_TYPE_MAP:
            raise SpecError("字段 %s 的未知类型 %s（支持：%s）"
                            % (self.name, self.type, ", ".join(sorted(TS_TYPE_MAP))))
        self.ts_type = TS_TYPE_MAP[self.type]
        self.getter = "get" + _upper0(self.name)
        self.setter = "set" + _upper0(self.name)
        self.upper = _upper0(self.name)


def validate_spec(data: dict) -> None:
    """规格契约校验：必填键 + 业务字段 + entity 命名。"""
    for key in ("module", "entity", "table", "comment"):
        if key not in data or not str(data[key]).strip():
            raise SpecError("规格缺少必填键 %s" % key)
    entity = str(data["entity"]).strip()
    if not re.match(r"^[A-Z][A-Za-z0-9]*$", entity):
        raise SpecError("entity 必须为 UpperCamelCase（如 AlarmRule），当前：%s" % entity)
    if not data.get("fields"):
        raise SpecError("规格缺少 fields（至少一个业务字段）")


def load_spec(spec_path: Path) -> dict:
    data = json.loads(spec_path.read_text(encoding="utf-8"))
    validate_spec(data)
    return data


def build_model(spec: dict) -> dict:
    validate_spec(spec)
    module = str(spec["module"]).strip()
    entity = str(spec["entity"]).strip()
    table = str(spec["table"]).strip()
    comment = str(spec["comment"]).strip()
    kebab = camel_to_kebab(entity)
    entity_lower = entity[0].lower() + entity[1:]
    perm_prefix = str(spec.get("permPrefix") or "%s:%s" % (module, kebab)).strip()
    package = "%s.%s" % (BASE_PACKAGE, module)

    fields = [Field(f, i) for i, f in enumerate(spec["fields"])]

    # 仅需额外 import 的类型（去重、保序、补全 import 语句）
    extra_imports = [JAVA_IMPORT_MAP[f.type] for f in fields if f.type in JAVA_IMPORT_MAP]
    seen = set()
    extra_imports = ["import %s;" % i for i in extra_imports if not (i in seen or seen.add(i))]

    def do_line(f: Field) -> str:
        if f.comment:
            return "    /** %s */\n    private %s %s;" % (f.comment, f.type, f.name)
        return "    private %s %s;" % (f.type, f.name)

    def query_line(f: Field) -> str:
        return "    private %s %s;" % (f.type, f.name)

    def request_line(f: Field) -> str:
        lines = []
        if f.required:
            if f.type == "String":
                lines.append('    @NotBlank(message = "%s不能为空")' % f.comment)
            else:
                lines.append('    @NotNull(message = "%s不能为空")' % f.comment)
        if f.max:
            lines.append('    @Size(max = %s, message = "%s长度不能超过 %s")' % (f.max, f.comment, f.max))
        lines.append("    private %s %s;" % (f.type, f.name))
        return "\n".join(lines)

    def vo_line(f: Field) -> str:
        return "    private %s %s;" % (f.type, f.name)

    where_lines = []
    for f in fields:
        if f.query_like:
            where_lines.append('                .like(StringUtils.hasText(query.%s()), %sDO::%s, query.%s())'
                               % (f.getter, entity, f.getter, f.getter))
        elif f.query_eq:
            where_lines.append('                .eq(query.%s() != null, %sDO::%s, query.%s())'
                               % (f.getter, entity, f.getter, f.getter))

    set_lines = ["        entity.%s(request.%s());" % (f.setter, f.getter) for f in fields]
    vo_set_lines = ["                .%s(entity.%s())" % (f.name, f.getter) for f in fields]

    def ts_field_line(f: Field) -> str:
        # required 字段必填（无 ?），可空字段可选（带 ?）；TS 可选属性语法为 prop?: type
        marker = "" if f.required else "?"
        return "  %s%s: %s" % (f.name, marker, f.ts_type)

    ts_vo_fields = [ts_field_line(f) for f in fields]
    ts_req_fields = [ts_field_line(f) for f in fields]

    search_fields = ["  { label: '%s', prop: '%s', placeholder: '请输入%s' }," % (f.comment, f.name, f.comment)
                     for f in fields if f.query_like or f.query_eq]
    columns = ["  { title: '%s', dataIndex: '%s', key: '%s' }," % (f.comment, f.name, f.name) for f in fields]

    def param_line(f: Field) -> str:
        # buildParams 里的参数行：数字字段直接 as number，字符串字段空串转 undefined
        if f.type in ("Integer", "Long", "BigDecimal"):
            return "%s: query.%s as number | undefined," % (f.name, f.name)
        return "%s: (query.%s as string) || undefined," % (f.name, f.name)

    param_lines = [param_line(f) for f in fields if f.query_like or f.query_eq]

    def form_item(f: Field) -> str:
        label = f.comment.replace("'", "\\'")
        if f.type in ("Integer", "Long", "BigDecimal"):
            return ("        <a-form-item label=\"%s\">\n"
                    "          <a-input-number v-model:value=\"form.%s\" style=\"width: 100%%\" />\n"
                    "        </a-form-item>") % (label, f.name)
        return ("        <a-form-item label=\"%s\">\n"
                "          <a-input v-model:value=\"form.%s\" />\n"
                "        </a-form-item>") % (label, f.name)

    form_items = [form_item(f) for f in fields]

    return {
        "module": module,
        "Entity": entity,
        "entity": entity_lower,
        "kebab": kebab,
        "table": table,
        "comment": comment,
        "package": package,
        "permPrefix": perm_prefix,
        "imports": "\n".join(extra_imports) if extra_imports else "",
        "fields_do": [do_line(f) for f in fields],
        "fields_query": [query_line(f) for f in fields],
        "fields_request": [request_line(f) for f in fields],
        "fields_vo": [vo_line(f) for f in fields],
        "where_lines": where_lines,
        "set_lines": set_lines,
        "vo_set_lines": vo_set_lines,
        "ts_vo_fields": ts_vo_fields,
        "ts_req_fields": ts_req_fields,
        "search_fields": search_fields,
        "columns": columns,
        "form_items": form_items,
        "param_lines": param_lines,
    }


def output_files(model: dict) -> dict:
    """返回 {相对路径: 模板文件名} 清单。"""
    m = model
    pkg = m["package"]
    return {
        "backend/src/main/java/%s/entity/%sDO.java" % (pkg.replace(".", "/"), m["Entity"]): "EntityDO.java.tpl",
        "backend/src/main/java/%s/mapper/%sMapper.java" % (pkg.replace(".", "/"), m["Entity"]): "EntityMapper.java.tpl",
        "backend/src/main/java/%s/dto/%sQuery.java" % (pkg.replace(".", "/"), m["Entity"]): "EntityQuery.java.tpl",
        "backend/src/main/java/%s/dto/%sSaveRequest.java" % (pkg.replace(".", "/"), m["Entity"]): "EntitySaveRequest.java.tpl",
        "backend/src/main/java/%s/vo/%sVo.java" % (pkg.replace(".", "/"), m["Entity"]): "EntityVo.java.tpl",
        "backend/src/main/java/%s/%sService.java" % (pkg.replace(".", "/"), m["Entity"]): "EntityService.java.tpl",
        "backend/src/main/java/%s/%sController.java" % (pkg.replace(".", "/"), m["Entity"]): "EntityController.java.tpl",
        "frontend/src/api/%s.ts" % m["module"]: "api.ts.tpl",
        "frontend/src/views/%s/%s/index.vue" % (m["module"], m["kebab"]): "index.vue.tpl",
    }


def generate(spec: dict, templates_dir: Path) -> dict:
    """核心：返回 {相对路径: 渲染后文本}。模板渲染与写盘解耦，便于单测。"""
    model = build_model(spec)
    result = {}
    for rel_path, tpl_name in output_files(model).items():
        template = (templates_dir / tpl_name).read_text(encoding="utf-8")
        result[rel_path] = render(template, model)
    return result


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="零依赖 CRUD 代码生成器")
    parser.add_argument("spec", help="规格 JSON 文件路径")
    parser.add_argument("--out", default=str(PROJECT_ROOT), help="输出根目录（默认项目根）")
    parser.add_argument("--dry-run", action="store_true", help="仅打印将生成的文件清单，不写盘")
    parser.add_argument("--templates", default=str(SCRIPT_DIR / "templates"), help="模板目录")
    args = parser.parse_args(argv)

    spec_path = Path(args.spec)
    if not spec_path.exists():
        print("错误：规格文件不存在：%s" % spec_path, file=sys.stderr)
        return 2

    try:
        spec = load_spec(spec_path)
        files = generate(spec, Path(args.templates))
    except (SpecError, KeyError, json.JSONDecodeError) as exc:
        print("错误：%s" % exc, file=sys.stderr)
        return 2

    out_root = Path(args.out)
    for rel_path, content in sorted(files.items()):
        target = out_root / rel_path
        print("  -> %s" % rel_path)
        if not args.dry_run:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content, encoding="utf-8")
    print("\n共生成 %d 个文件%s" % (len(files), "（dry-run，未写盘）" if args.dry_run else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
