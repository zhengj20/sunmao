package com.reip.framework.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.reip.framework.auth.UserContext;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 乐观锁拦截器：@Version 字段变更时自动带 version 条件
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * 标准字段自动填充：创建人/时间、更新人/时间、租户、版本号
     */
    @Component
    public static class AutoFillHandler implements MetaObjectHandler {
        @Override
        public void insertFill(MetaObject metaObject) {
            Long uid = UserContext.currentUserId();
            LocalDateTime now = LocalDateTime.now();
            this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
            this.strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
            if (uid != null) {
                this.strictInsertFill(metaObject, "createdBy", Long.class, uid);
                this.strictInsertFill(metaObject, "updatedBy", Long.class, uid);
            }
            this.strictInsertFill(metaObject, "tenantId", Long.class, 1L);
            this.strictInsertFill(metaObject, "version", Integer.class, 0);
        }

        @Override
        public void updateFill(MetaObject metaObject) {
            Long uid = UserContext.currentUserId();
            this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            if (uid != null) {
                this.strictUpdateFill(metaObject, "updatedBy", Long.class, uid);
            }
        }
    }
}
