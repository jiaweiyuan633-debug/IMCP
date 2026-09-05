package cn.admin.scaffold.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import cn.admin.scaffold.security.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        // 非严格填充：对所有带 createdBy/createdAt/updatedAt/updatedBy 属性的实体自动填充，
        // 无需在每个实体上声明 @TableField(fill)（实体缺少某字段时自动跳过）
        setFieldValByName("createdAt", now, metaObject);
        setFieldValByName("updatedAt", now, metaObject);
        setFieldValByName("createdBy", SecurityUtils.tryGetUserId(), metaObject);
        setFieldValByName("updatedBy", SecurityUtils.tryGetUserId(), metaObject);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        setFieldValByName("updatedAt", LocalDateTime.now(), metaObject);
        setFieldValByName("updatedBy", SecurityUtils.tryGetUserId(), metaObject);
    }
}

