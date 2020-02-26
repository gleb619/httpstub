package org.hidetake.stubyaml.service.rules;

import lombok.extern.slf4j.Slf4j;
import org.hidetake.stubyaml.model.yaml.RouteSource;
import org.hidetake.stubyaml.model.yaml.Rule;
import org.hidetake.stubyaml.model.yaml.RuleContainer;
import org.hidetake.stubyaml.service.YamlParser;
import org.yaml.snakeyaml.constructor.ConstructorException;

import java.util.Arrays;

@Slf4j
public abstract class RulesV11Compiler extends RulesV10Compiler {

    public RulesV11Compiler(YamlParser yamlParser) {
        super(yamlParser);
    }

    @Override
    public Object compile(RouteSource routeSource) {
        RuleContainer container;
        try {
            container = yamlParser.parse(routeSource.getFile(), RuleContainer.class);
        } catch (ConstructorException skip) {
            Rule[] rules = (Rule[]) super.compile(routeSource);
            try {
                container = RuleContainer.builder()
                    .rules(Arrays.asList(rules))
                    .version(RuleContainer.Version.V10.name())
                    .build();
            } catch (Exception e) {
                log.error("ERROR: ", skip);
                throw e;
            }
        }

        return container;
    }

}
