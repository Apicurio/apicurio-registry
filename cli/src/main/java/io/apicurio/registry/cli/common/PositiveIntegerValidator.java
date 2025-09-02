package io.apicurio.registry.cli.common;

import picocli.CommandLine.IParameterConsumer;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

import java.util.Stack;

public class PositiveIntegerValidator implements IParameterConsumer {

    @Override
    public void consumeParameters(Stack<String> args, ArgSpec argSpec, CommandSpec commandSpec) {
        if (args.isEmpty()) {
            throw new ParameterException(
                    commandSpec.commandLine(),
                    "Missing required parameter for option '%s'".formatted(argSpec.paramLabel())
            );
        }
        String value = args.pop();
        try {
            int intValue = Integer.parseInt(value);
            if (intValue <= 0) {
                throw new ParameterException(
                        commandSpec.commandLine(),
                        "Invalid value '%s' for option '%s': value must be greater than 0".formatted(value, argSpec.paramLabel())
                );
            }
            argSpec.setValue(intValue);
        } catch (NumberFormatException e) {
            throw new ParameterException(
                    commandSpec.commandLine(),
                    "Invalid value '%s' for option '%s': must be an integer".formatted(value, argSpec.paramLabel())
            );
        }
    }
}

