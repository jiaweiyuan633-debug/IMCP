package com.example.admin.module.auth.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CaptchaResponse {

    private String captchaId;
    private String image;
}

