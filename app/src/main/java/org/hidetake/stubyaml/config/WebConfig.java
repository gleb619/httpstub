package org.hidetake.stubyaml.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

@Order
@Configuration
public class WebConfig {

    @Bean
    @ConditionalOnMissingBean
    public Yaml appYamlMapper() {
        Representer representer = new Representer();
        representer.getPropertyUtils().setSkipMissingProperties(true);

        return new Yaml(new Constructor(),
            representer,
            new DumperOptions(),
            new LoaderOptions(),
            new Resolver());
    }

}
