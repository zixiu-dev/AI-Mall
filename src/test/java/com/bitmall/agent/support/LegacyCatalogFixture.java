package com.bitmall.agent.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.jdbc.Sql;

/**
 * 旧交易与搜索场景的显式数据夹具。仍执行全部 Flyway 迁移，只在本用例中启用旧目录，
 * 保留历史用例特意构造的价格、颜色、尺码与缺货边界；真实目录另用隔离数据库验收。
 * 此注解与 SQL 只放在 test 目录，不进入商城应用。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Sql(scripts = "/legacy-catalog-fixture.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
public @interface LegacyCatalogFixture {}
