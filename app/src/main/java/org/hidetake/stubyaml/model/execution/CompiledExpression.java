package org.hidetake.stubyaml.model.execution;

import com.google.common.base.Throwables;
import groovy.lang.Binding;
import groovy.lang.Script;
import lombok.Builder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.codehaus.groovy.runtime.StackTraceUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Data
@Slf4j
@Builder
public class CompiledExpression {

    public static final Pattern PATTERN = Pattern.compile("\\.groovy:(\\d+)");

    // a class to evaluate the script, compiled by Groovy
    private final Class<Script> clazz;
    private final String source;

    @SneakyThrows
    public Object evaluate(Bindable bindable) {
        Object output;
        try {
            final var script = clazz.getConstructor().newInstance();
            Binding binding = new Binding(bindable.getBinding());
            script.setBinding(binding);
            output = script.run();
        } catch (Exception ex) {
            throw handleError(ex);
        }

        return output;
    }

    private RuntimeException handleError(Exception ex) {
        Throwable e = StackTraceUtils.deepSanitize(ex);
        String stackTrace = Throwables.getStackTraceAsString(e);
        Integer lineNumber = parseLineNumber(stackTrace);
        String line = getLine(source, lineNumber);
        String errorMessage = String.format("Error at line %s:`%s` <- %s", lineNumber, line, e.getMessage());

        if(log.isDebugEnabled()) {
            log.error(errorMessage, e);
        } else {
            log.error(errorMessage);
        }

        return new RuntimeException(errorMessage);
    }

    private String getLine(String stackTrace, Integer line) {
        String[] lines = stackTrace.split("\n|\r");
        if(line < 1) line = 1;
        if(line > lines.length) line = lines.length;

        return lines[line - 1].trim();
    }

    public Integer parseLineNumber(String stackTrace) {
        Matcher matcher = PATTERN.matcher(stackTrace);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return 0;
    }

}
