package org.hidetake.stubyaml.model;

import lombok.RequiredArgsConstructor;
import org.hidetake.stubyaml.model.exception.IllegalRuleException;
import org.hidetake.stubyaml.model.execution.CompiledExpression;
import org.hidetake.stubyaml.model.execution.CompiledResponse;
import org.hidetake.stubyaml.model.execution.CompiledResponseBody;
import org.hidetake.stubyaml.model.execution.FileBody;
import org.hidetake.stubyaml.model.yaml.Response;
import org.hidetake.stubyaml.model.yaml.RouteSource;
import org.hidetake.stubyaml.service.ObjectCompiler;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

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
            .headers(compileHeaders(response.getHeaders(), source))
            .body(compileBody(response, source))
            .tables(tableCompiler.compile(response.getTables(), source))
            .delay(computeDelay(response, source))
            .build();
    }

    private MultiValueMap<String, CompiledExpression> compileHeaders(Map<String, Object> headers, RouteSource source) {
        final var target = new LinkedMultiValueMap<String, CompiledExpression>(headers.size());
        headers.forEach((key, value) -> {
            if (value instanceof List<?>) {
                target.addAll(key, compileHeaderValues((List<?>) value, source));
                return;
            }
            if (value instanceof String) {
                target.add(key, expressionCompiler.compileTemplate((String) value, source));
                return;
            }
            throw new IllegalRuleException("header value must be a string or list: " + value, source);
        });
        return target;
    }

    private List<CompiledExpression> compileHeaderValues(List<?> values, RouteSource source) {
        return values.stream().map(value -> {
            if (value instanceof String) {
                return expressionCompiler.compileTemplate((String) value, source);
            }
            throw new IllegalRuleException("header value must be a string: " + value, source);
        }).collect(toList());
    }

    private CompiledResponseBody<?> compileBody(Response response, RouteSource source) {
        final var body = response.getBody();
        final var file = response.getFile();
        if (body != null && file != null) {
            throw new IllegalStateException("Either body or file must be provided");
        }

        if (body == null && file == null) {
            return new CompiledResponseBody.NullBody();
        }
        if (body != null) {
            return new CompiledResponseBody.PrimitiveBody(compilePrimitiveBody(body, source));
        } else {
            final var filenameExpression = expressionCompiler.compileTemplate(file, source);
            final var baseDirectory = source.getFile().getParentFile();
            return new FileBody(filenameExpression, baseDirectory, expressionCompiler);
        }
    }

    private Object compilePrimitiveBody(Object body, RouteSource source) {
        if (body instanceof String) {
            final var string = (String) body;
            return expressionCompiler.compileTemplate(string, source);
        } else if (body instanceof List) {
            final var list = (List<?>) body;
            return list.stream()
                .map(item -> compilePrimitiveBody(item, source))
                .collect(toList());
        } else if (body instanceof Map) {
            final var map = (Map<?, ?>) body;
            return mapValue(map, item -> compilePrimitiveBody(item, source));
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
