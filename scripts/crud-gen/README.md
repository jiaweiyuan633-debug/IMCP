# CRUD 代码生成器（零依赖）

> R4-1.36（批次9）引入。定位：**标准 CRUD 骨架**的快速产出，复杂业务在生成后手写扩展。
> 与 `docs/architecture-conventions.md` 修订后的约定一致：`标准 CRUD 用轻量生成器，复杂业务手写`。

## 环境

- 仅依赖 Python 3.10+ 标准库（无第三方包，离线可跑）。
- 生成器本体重在「模板渲染 + 字段派生」，不连数据库；表结构以规格 JSON 描述。

## 用法

```bash
python scripts/crud-gen/crud_gen.py scripts/crud-gen/spec.example.json          # 生成到项目根（后端+前端）
python scripts/crud-gen/crud_gen.py path/to/spec.json --out /tmp/out            # 指定输出根（便于预览）
python scripts/crud-gen/crud_gen.py path/to/spec.json --dry-run                 # 仅打印文件清单
```

## 规格文件（spec.json）

```jsonc
{
  "module": "device",                  // 后端 module 包名、前端 api/views 目录名
  "entity": "AlarmRule",               // UpperCamelCase，类名前缀
  "table": "device_alarm_rule",        // 数据库表名
  "comment": "设备告警规则",            // 中文说明，用于类注释 / OperLog / 页面标题
  "permPrefix": "device:alarm-rule",   // 权限编码前缀（默认 module:kebab）
  "fields": [
    { "name": "name", "type": "String", "comment": "规则名称", "queryLike": true, "required": true, "max": 50 },
    { "name": "severity", "type": "Integer", "comment": "级别", "queryEq": true, "required": true }
  ]
}
```

字段属性：

| 属性 | 含义 | 影响 |
| --- | --- | --- |
| `name` | 字段名（lowerCamelCase） | 属性/列映射 |
| `type` | Java 类型（见下表） | 类型映射与 import |
| `comment` | 中文说明 | 注释 / 校验文案 / 页面列与表单 label |
| `queryLike` | 模糊查询 | Query 字段 + Service `.like` + 前端搜索框 |
| `queryEq` | 等值查询 | Query 字段 + Service `.eq` + 前端搜索框 |
| `required` | 必填 | SaveRequest `@NotBlank`/`@NotNull` |
| `max` | 最大长度 | SaveRequest `@Size` |

支持类型：`String` / `Long` / `Integer` / `BigDecimal` / `LocalDateTime` / `LocalDate` / `Boolean`。

## 生成物（9 个文件）

后端（`backend/src/main/java/com/example/admin/module/{module}/`）：

| 文件 | 说明 |
| --- | --- |
| `entity/{Entity}DO.java` | 表实体：`@TableId(AUTO)` + 业务字段 + 审计/租户/逻辑删除/乐观锁骨架 |
| `mapper/{Entity}Mapper.java` | `BaseMapper<DO>` |
| `dto/{Entity}Query.java` | 分页查询参数（pageNum/pageSize + 查询字段） |
| `dto/{Entity}SaveRequest.java` | 新增/编辑请求（`@Valid` 校验注解） |
| `vo/{Entity}Vo.java` | 展示对象（`@Builder` + 审计字段） |
| `{Entity}Service.java` | 分页/详情/新增/编辑/删除 + `requireById/toEntity/toVo` |
| `{Entity}Controller.java` | REST 接口（`@PreAuthorize` 五权限 + `@OperLog` + `@Valid`） |

前端：

| 文件 | 说明 |
| --- | --- |
| `src/api/{module}.ts` | 接口 + `Vo/SaveRequest` 类型 + page/create/update/delete 函数 |
| `src/views/{module}/{kebab}/index.vue` | 列表页骨架（搜索 + 表格 + 弹窗表单，中文直写不依赖 i18n） |

## 生成后必做

1. 按 `docs/database/README.md` 的约定补一张 Flyway 迁移创建 `table`（含 `tenant_id/created_at/updated_at/created_by/updated_by/version/deleted` 列），并在 `sys_data_permission` 注册数据权限（如需）。
2. 按 V60 之后的菜单迁移规范，用 `perm` 动态解析为新模块插入菜单与按钮并授权 `role_id=1`。
3. 运行 `mvn -o test` 确保生成的 Service 有单元测试（JaCoCo 门禁会拦截未测试的新代码）。

## 开发与测试

模板与生成器均为纯文本，改模板后跑单测即可验证：

```bash
cd scripts/crud-gen
python -m unittest discover -s tests -v
```

单测覆盖：模板引擎（变量/循环）、字段派生行（DO/Query/Request/VO/where/set/TS）、
输出文件清单、类型映射、kebab 转换、规格校验失败分支。
