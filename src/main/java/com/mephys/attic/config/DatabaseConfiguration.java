package com.mephys.attic.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import com.zaxxer.hikari.HikariDataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
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

	/** A database migrated with what V1 now replaces is marked as V1 first; see {@link SquashedMigrations} */
	@Bean
	FlywayMigrationStrategy squashedMigrations() {
		return (flyway) -> {
			try {
				SquashedMigrations.convert(flyway.getConfiguration().getDataSource());
			}
			catch (SQLException ex) {
				throw new IllegalStateException("The history of the migrations could not be read", ex);
			}
			flyway.migrate();
		};
	}

}
