package com.example.admin.module.system;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class PostIdsRequest {

    @NotEmpty(message = "岗位列表不能为空")
    private List<Long> postIds;
}
