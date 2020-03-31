package org.hidetake.stubyaml.model;

import groovy.lang.GroovyClassLoader;
import lombok.RequiredArgsConstructor;
import org.hidetake.stubyaml.app.StubInitializer;
import org.hidetake.stubyaml.model.execution.CompiledExpression;
import org.hidetake.stubyaml.model.yaml.RouteSource;
import org.hidetake.stubyaml.service.ObjectCompiler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ExpressionCompiler implements ObjectCompiler {

    public final GroovyClassLoader GROOVY_CLASS_LOADER = new GroovyClassLoader();
    public static final String MULTI_STRINGS = "\"\"\"";

    private final StubInitializer.Config config;

    /**
     * Compile a Groovy expression.
     * For example, {@code 1 + 1} will be {@code 2} on evaluated.
     *
     * @param expression Groovy expression
     * @param source
     * @return compiled
     */
    @SuppressWarnings("unchecked")
    public CompiledExpression compileExpression(String expression, RouteSource source) {
        if (expression == null) {
            return null;
        }
        final var fileName = source.getFile().getName().replace(".", "-");
        final var newName = String.format("script-%s-%s.groovy", fileName, Math.abs(expression.hashCode()));
        final var clazz = GROOVY_CLASS_LOADER.parseClass(expression, newName);

        return CompiledExpression.builder()
            .clazz(clazz)
            .source(expression)
            .build();
    }

    /**
     * Compile a Groovy template.
     * If it begins with <code>${</code> and ends with <code>}</code>, it will be treated as an expression.
     * For example, {@code user${id}} will be {@code user100} if {@code id} is {@code 100}.
     *
     * @param template Groovy template
     * @param source
     * @return compiled
     */
    public CompiledExpression compileTemplate(String template, RouteSource source) {
        if (template == null) return null;
        String localTemplate = template.trim();
        if (localTemplate.startsWith("${") && localTemplate.endsWith("}")) {
            return compileExpression(localTemplate.substring(2, localTemplate.length() - 1), source);
        }
        String newText = String.format("%s%s%s",
            MULTI_STRINGS,
            StringUtils.replace(localTemplate, MULTI_STRINGS, "\\\"\\\"\\\""),
            MULTI_STRINGS);

        return compileExpression(newText, source);
    }

}
