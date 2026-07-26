package com.ksh.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration.
 *
 * <p>Public avatar and exam files are served by
 * {@link com.ksh.features.upload.PublicUploadsController}. Private lesson,
 * library and practice objects are delivered only through their authorized
 * controllers, so no broad {@code /uploads/**} disk handler is registered.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {
    // Intentionally empty.
}
