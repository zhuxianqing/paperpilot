package com.paperpilot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${export.local-path:./exports}")
    private String exportPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 配置导出文件目录为静态资源，便于下载
        Path path = Paths.get(exportPath).toAbsolutePath().normalize();
        registry.addResourceHandler("/exports/**")
                .addResourceLocations("file:" + path.toString() + "/");
    }
}
