package org.hidetake.stubyaml.model.execution;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hidetake.stubyaml.model.ExpressionCompiler;
import org.hidetake.stubyaml.model.yaml.FilenameRouteSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    @Override
    @SneakyThrows
    public HttpHeaders evaluateHeaders(ResponseContext responseContext, HttpHeaders headers) {
        HttpHeaders output = new HttpHeaders();
        if(Objects.isNull(headers.getContentType())) {
            MediaType mediaType = parseMediaType(responseContext);
            if(Objects.nonNull(mediaType)) {
                output.setContentType(mediaType);
            }
        }

        return output;
    }

    /* ===================== */

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

        String originalFileExtension = com.google.common.io.Files.getFileExtension(file.getName());
        File tempFile = File.createTempFile(file.getName() + "-" + hashcode + "-", "." + originalFileExtension);
        try {
            Files.write(tempFile.toPath(), evaluatedResult.getBytes(StandardCharsets.UTF_8));
        } finally {
            tempFile.deleteOnExit();
        }

        return tempFile;
    }

    private MediaType parseMediaType(ResponseContext responseContext) {
        try {
            String newHash = Math.abs(responseContext.hashCode()) + "";
            File compiledFile = cache.get(newHash);
            String fileType = Files.probeContentType(compiledFile.toPath());
            MimeType type = MimeType.valueOf(fileType);
            return new MediaType(type.getType(), type.getSubtype(), type.getParameters());
        } catch (Exception e) {
            return null;
        }
    }

}
