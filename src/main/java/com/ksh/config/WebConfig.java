package com.ksh.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC mapping for locally stored uploads.
 *
 * <p>Files are stored on the local disk under the directory configured by
 * {@code app.upload.dir} (defaults to {@code uploads} relative to the working directory).
 * The resource handler only maps URL paths to disk. {@code SecurityConfig}
 * separately permits public avatar paths and denies private practice namespaces;
 * private practice material is delivered by an authorized controller.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final Path uploadDir;

    /**
     * Creates a new {@code WebConfig} instance.
     *
     * @param uploadDir the root directory for user uploads, sourced from
     *                  {@code app.upload.dir}; defaults to {@code uploads} if the
     *                  property is not set
     */
    public WebConfig(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir.toString().replace("\\", "/") + "/");
    }
}
