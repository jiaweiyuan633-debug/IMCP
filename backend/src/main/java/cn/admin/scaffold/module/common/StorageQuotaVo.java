package cn.admin.scaffold.module.common;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StorageQuotaVo {

    private Long usedBytes;
    private Long limitBytes;
    private Integer percent;
    private Boolean unlimited;
}
