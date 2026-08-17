package com.example.admin.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 逻辑删除 + 业务编码唯一键冲突的通用解法（批次4·R4-1.50）。
 *
 * <p>问题：MyBatis-Plus 逻辑删除后行仍在，但 (tenant_id, 业务编码) 唯一键仍占位，
 * 删除后同名/同编码数据永远无法重建（SystemUserService.create 的 exists 检查自动
 * 过滤 deleted=0 判定"不存在"，INSERT 却命中唯一键返回"数据已存在"）。
 *
 * <p>解法（评审方案③改良）：删除前先把业务编码改为「原编码#del#时间戳」，释放
 * 唯一键、保留逻辑删除行（审计可追溯），同名数据可立即重建。约定：凡新增表
 * 禁止再出现「逻辑删除 + 业务编码唯一键」组合（见 docs/architecture-conventions.md）。
 */
public final class UniqueKeyRelease {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private UniqueKeyRelease() {
    }

    /** 生成释放用编码：原编码 + 删除标记 + 毫秒时间戳（保证唯一且可辨识）。 */
    public static String releaseCode(String original) {
        return original + "#del#" + LocalDateTime.now().format(FORMATTER);
    }
}
