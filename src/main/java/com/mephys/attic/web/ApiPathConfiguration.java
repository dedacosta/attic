package com.mephys.attic.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves every REST controller below {@code /api}, apart from the web page itself.
 */
@Configuration(proxyBeanMethods = false)
class ApiPathConfiguration implements WebMvcConfigurer {

	static final String API_PREFIX = "/api";

	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix(API_PREFIX, HandlerTypePredicate.forAnnotation(RestController.class));
	}

}
