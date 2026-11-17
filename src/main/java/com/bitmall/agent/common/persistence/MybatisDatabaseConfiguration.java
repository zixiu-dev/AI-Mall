package com.bitmall.agent.common.persistence;

import java.util.Properties;
import org.apache.ibatis.mapping.DatabaseIdProvider;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Select database-specific SQL only where the dialect requires it. */
@Configuration
public class MybatisDatabaseConfiguration {
  @Bean
  public DatabaseIdProvider databaseIdProvider() {
    VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
    Properties names = new Properties();
    names.setProperty("MySQL", "mysql");
    names.setProperty("H2", "h2");
    provider.setProperties(names);
    return provider;
  }
}
