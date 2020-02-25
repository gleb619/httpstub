package org.hidetake.stubyaml.model;

import lombok.RequiredArgsConstructor;
import org.hidetake.stubyaml.model.exception.IllegalRuleException;
import org.hidetake.stubyaml.model.execution.CompiledResponse;
import org.hidetake.stubyaml.model.execution.CompiledResponseBody;
import org.hidetake.stubyaml.model.yaml.Response;
import org.hidetake.stubyaml.model.yaml.RouteSource;
import org.hidetake.stubyaml.service.ObjectCompiler;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.hidetake.stubyaml.util.MapUtils.mapValue;

@RequiredArgsConstructor
@Component
public class ResponseCompiler implements ObjectCompiler {

    private final ExpressionCompiler expressionCompiler;
    private final TableCompiler tableCompiler;

    public CompiledResponse compile(Response response, RouteSource source) {
        Assert.notNull(response, "response should not be null");
        Assert.notNull(response.getHeaders(), "headers should not be null");

        return CompiledResponse.builder()
            .status(response.getStatus())
            .headers(mapValue(response.getHeaders(), value -> expressionCompiler.compileTemplate(value)))
            .body(compileBody(response, source))
            .tables(tableCompiler.compile(response.getTables(), source))
            .delay(computeDelay(response, source))
            .build();
    }

    private CompiledResponseBody compileBody(Response response, RouteSource source) {
        final var body = response.getBody();
        final var file = response.getFile();
        if (body != null && file != null) {
            throw new IllegalStateException("Either body or file must be provided");
        }

        if (body == null && file == null) {
            return new CompiledResponseBody.NullBody();
        }
        if (body != null) {
            return new CompiledResponseBody.PrimitiveBody(compilePrimitiveBody(body));
        } else {
            final var filenameExpression = expressionCompiler.compileTemplate(file);
            final var baseDirectory = source.getFile().getParentFile();
            return new CompiledResponseBody.FileBody(filenameExpression, baseDirectory);
        }
    }

    private Object compilePrimitiveBody(Object body) {
        if (body instanceof String) {
            final var string = (String) body;
            return expressionCompiler.compileTemplate(string);
        } else if (body instanceof List) {
            final var list = (List<?>) body;
            return list.stream()
                .map(item -> compilePrimitiveBody(item))
                .collect(toList());
        } else if (body instanceof Map) {
            final var map = (Map<?, ?>) body;
            return mapValue(map, item -> compilePrimitiveBody(item));
        } else {
            return body;
        }
    }

    private Duration computeDelay(Response response, RouteSource source) {
        final var delay = response.getDelay();
        if (delay >= 0) {
            return Duration.ofMillis(delay);
        } else {
            throw new IllegalRuleException("Invalid delay: " + delay, source);
        }
    }

}