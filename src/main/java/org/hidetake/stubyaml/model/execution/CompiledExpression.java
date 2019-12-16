package org.hidetake.stubyaml.model.execution;

import groovy.lang.Binding;
import groovy.lang.Script;
import lombok.Data;
import lombok.SneakyThrows;

@Data
public class CompiledExpression {

    private final Class<Script> clazz;

    @SneakyThrows
    public Object evaluate(Bindable bindable) {
        final var script = clazz.newInstance();
        Binding binding = new Binding(bindable.getBinding());
        script.setBinding(binding);
        Object output = script.run();

        return output;
    }

}
