package org.hidetake.stubyaml;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.hidetake.stubyaml.model.RouteCompiler;
import org.hidetake.stubyaml.model.RouteScanner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ObjectUtils;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class StubConfiguration {
    @Getter
    @Setter
    private String path = "data";

    private final RouteScanner routeScanner;
    private final RouteCompiler routeCompiler;

    @Bean
    RequestMappingHandlerMapping stubRequestHandlerMapping() throws NoSuchMethodException, IOException {
        val mapping = new RequestMappingHandlerMapping();
        mapping.setOrder(Integer.MAX_VALUE - 2);

        val handleMethod = StubController.class.getMethod("handle",
            HttpServletRequest.class,
            Map.class,
            Map.class,
            Map.class,
            Object.class);

        val baseDirectory = new File(path);
        if (baseDirectory.isDirectory()) {
            if (!ObjectUtils.isEmpty(baseDirectory.listFiles())) {
                log.info("Scanning data directory {}", baseDirectory.getAbsolutePath());
                routeScanner.scan(baseDirectory)
                    .map(routeCompiler::compile)
                    .forEach(route -> {
                        val requestMappingInfo = route.getRequestMappingInfo();
                        val controller = new StubController(route);
                        log.info("Mapping route {}", route);
                        mapping.registerMapping(requestMappingInfo, controller, handleMethod);
                    });
            } else {
                log.warn("No rule found in {}", baseDirectory.getAbsolutePath());
            }
        } else {
            log.warn("No data directory {}", baseDirectory.getAbsolutePath());
        }

        return mapping;
    }
}
