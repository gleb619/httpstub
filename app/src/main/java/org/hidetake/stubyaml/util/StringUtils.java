package org.hidetake.stubyaml.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.function.Supplier;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StringUtils {

    public static Supplier<String> format(String text, Object... args) {
        return () -> String.format(text, args);
    }

}