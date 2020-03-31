package org.hidetake.stubyaml.model.execution;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hidetake.stubyaml.model.ExpressionCompiler;
import org.hidetake.stubyaml.model.yaml.FilenameRouteSource;
import org.springframework.util.MimeType;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.hidetake.stubyaml.util.StringUtils.firstNonEmpty;
import static org.springframework.util.ObjectUtils.nullSafeToString;

@RequiredArgsConstructor
public class FileBody implements CompiledResponseBody<File> {

    private static final List<String> TEXT_TYPES = ImmutableList.of(
        "json",
        "xml",
        "html",
        "htm",
        "css",
        "csv",
        "js"
    );

    private final CompiledExpression filenameExpression;
    private final File baseDirectory;
    private final ExpressionCompiler expressionCompiler;
    //TODO: replace with expirable cache
    private final Map<String, File> cache = Maps.newConcurrentMap();

    @Override
    public File evaluate(ResponseContext responseContext) {
        final var filename = nullSafeToString(filenameExpression.evaluate(responseContext));
        final var file = new File(baseDirectory, filename);

        if(isTextFile(file)) {
            String newHash = Math.abs(responseContext.hashCode()) + "";
            if(!cache.containsKey(newHash)) {
                File newCompiledFile = evaluateTextFile(file, responseContext, newHash);
                cache.put(newHash, newCompiledFile);
            }

            return cache.get(newHash);
        } else if (file.exists()) {
            return file;
        } else {
            throw new IllegalStateException("No such file: " + file.getAbsolutePath());
        }
    }

    private boolean isTextFile(File file) {
        try {
            String type = firstNonEmpty(Files.probeContentType(file.toPath()), "");
            MimeType mimeType = MimeType.valueOf(type);
            boolean isText = "text".equals(mimeType.getType());
            boolean isTextSubtype = TEXT_TYPES.contains(mimeType.getSubtype());

            return isText || isTextSubtype;
        } catch (Exception skip) {
            return false;
        }
    }

    @SneakyThrows
    private File evaluateTextFile(File file, ResponseContext responseContext, String hashcode) {
        String text = new String(Files.readAllBytes(file.toPath()));
        FilenameRouteSource source = new FilenameRouteSource(file);
        CompiledExpression compiledBody = expressionCompiler.compileTemplate(text, source);
        String evaluatedResult = String.valueOf(compiledBody.evaluate(responseContext));

        File tempFile = File.createTempFile(file.getName() + hashcode, null);
        try {
            Files.write(tempFile.toPath(), evaluatedResult.getBytes(StandardCharsets.UTF_8));
        } finally {
            tempFile.deleteOnExit();
        }

        return tempFile;
    }

}
