package org.hidetake.stubyaml.model.execution;

import com.google.common.hash.Hashing;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hidetake.stubyaml.model.ExpressionCompiler;
import org.springframework.util.MimeType;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.stream.Collectors.toList;
import static org.hidetake.stubyaml.util.MapUtils.mapValue;
import static org.springframework.util.ObjectUtils.nullSafeToString;

public interface CompiledResponseBody<T> {

    T evaluate(ResponseContext responseContext);

    class NullBody implements CompiledResponseBody<Object> {
        @Override
        public Object evaluate(ResponseContext responseContext) {
            return null;
        }

    }

    @RequiredArgsConstructor
    class PrimitiveBody implements CompiledResponseBody<Object> {
        private final Object body;

        private static Object evaluate(Object body, ResponseContext responseContext) {
            if (body == null || body instanceof Number || body instanceof Boolean) {
                return body;
            } else if (body instanceof CompiledExpression) {
                final var expression = (CompiledExpression) body;
                final var value = expression.evaluate(responseContext);
                return evaluate(value, responseContext);
            } else if (body instanceof List) {
                final var list = (List<?>) body;
                return list.stream()
                    .map(e -> evaluate(e, responseContext))
                    .collect(toList());
            } else if (body instanceof Map) {
                final var map = (Map<?, ?>) body;
                return mapValue(map, v -> evaluate(v, responseContext));
            } else {
                return body.toString();
            }
        }

        @Override
        public Object evaluate(ResponseContext responseContext) {
            return evaluate(body, responseContext);
        }

    }

    @RequiredArgsConstructor
    class FileBody implements CompiledResponseBody<File> {

        private final CompiledExpression filenameExpression;
        private final File baseDirectory;
        private final ExpressionCompiler expressionCompiler;
        private File compiledFile;
        private String compiledFileHash;

        @Override
        public File evaluate(ResponseContext responseContext) {
            final var filename = nullSafeToString(filenameExpression.evaluate(responseContext));
            final var file = new File(baseDirectory, filename);

            if(!isBinaryFile(file)) {
                String newHash = hash(file);
                if(compiledFileHash == null || !Objects.equals(compiledFileHash, newHash)) {
                    compiledFile = evaluateTextFile(file, responseContext, newHash);
                    compiledFileHash = newHash;
                }
            }

            if (compiledFile.exists()) {
                return compiledFile;
            } if (file.exists()) {
                return file;
            } else {
                throw new IllegalStateException("No such file: " + file.getAbsolutePath());
            }
        }

        @SneakyThrows
        private String hash(File file) {
            return com.google.common.io.Files.asByteSource(file).hash(Hashing.murmur3_128()).toString();
        }

        @SneakyThrows
        private File evaluateTextFile(File file, ResponseContext responseContext, String hashcode) {
            String text = new String(Files.readAllBytes(file.toPath()));
            CompiledExpression compiledBody = expressionCompiler.compileTemplate(text);
            compiledBody.evaluate(responseContext);

            File tempFile = File.createTempFile(file.getName(), hashcode);
            try {
                Files.write(tempFile.toPath(), text.getBytes(StandardCharsets.UTF_8));
            } finally {
                tempFile.deleteOnExit();
            }

            return tempFile;
        }

        @SneakyThrows
        public boolean isBinaryFile(File f) {
            String type = Files.probeContentType(f.toPath());

            if (type == null) {
                return true;
            } else {
                try {
                    MimeType mimeType = MimeType.valueOf(type);
                } catch (Exception e) {
                    return true;
                }

                return false;
            }
        }

    }

}
