package com.bitmall.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 项目启动入口。 main 方法启动 Spring Boot，扫描 Controller、Service、Mapper 等组件，并加载配置和数据库迁移。 阅读业务流程时可以先跳过这里，直接从对应
 * Controller 开始。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BitMallAgentApplication {

    /** 启动 Spring Boot 容器，自动装配 HTTP 接口、业务组件、数据库连接和配置。 */
    public static void main(String[] args) {
        SpringApplication.run(BitMallAgentApplication.class, args);
    }
}
