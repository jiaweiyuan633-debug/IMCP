package com.example.admin.module.system;

import lombok.Data;

import java.util.List;

@Data
public class PostIdsRequest {

    private List<Long> postIds;
}
