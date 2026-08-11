package com.example.admin.module.form.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class FormInstanceVo {

    private Long id;
    private Long formId;
    private String formCode;
    /** 提交数据（data_json 反序列化结果） */
    private Map<String, Object> data;
    /** SUBMITTED/APPROVED/REJECTED */
    private String status;
    private Long submitterId;
    private LocalDateTime submittedAt;
    private String remark;
    private LocalDateTime createdAt;
}
