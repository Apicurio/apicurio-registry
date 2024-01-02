package io.apicurio.registry.storage.error;

import io.apicurio.registry.types.RuleType;
import lombok.Getter;

public class RuleNotFoundException extends NotFoundException {

    private static final long serialVersionUID = -5024749463194169679L;


    @Getter
    private final RuleType rule;


    public RuleNotFoundException(RuleType rule) {
        super(message(rule));
        this.rule = rule;
    }

    public RuleNotFoundException(RuleType rule, Throwable cause) {
        super(message(rule), cause);
        this.rule = rule;
    }


    private static String message(RuleType rule) {
        return "No rule named '" + rule.name() + "' was found.";
    }

}
