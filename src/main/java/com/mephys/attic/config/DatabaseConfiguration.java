package com.mephys.attic.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.zaxxer.hikari.HikariDataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class DatabaseConfiguration {

	@Bean
	@ConfigurationProperties("spring.datasource.hikari")
	HikariDataSource dataSource(DataSourceProperties properties, @Value("${attic.database.file}") Path file)
			throws IOException {
		Path parent = file.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
	}

}
