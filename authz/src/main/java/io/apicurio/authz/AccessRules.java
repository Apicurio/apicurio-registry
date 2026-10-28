package io.apicurio.authz;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;

import io.kroxylicious.authorizer.provider.acl.AclAuthorizer;
import io.kroxylicious.authorizer.provider.acl.parser.AclRulesLexer;
import io.kroxylicious.authorizer.provider.acl.parser.AclRulesParser;
import io.kroxylicious.authorizer.service.Authorizer;
import io.kroxylicious.authorizer.service.ResourceType;
import io.kroxylicious.identity.Identity;
import io.kroxylicious.identity.Principal;

/**
 * An access rules file in the Kroxylicious ACL rules format, parsed with the Kroxylicious rules
 * parser ({@code AclRulesParser}). It provides both the point decisions, from a Kroxylicious
 * {@link AclAuthorizer} built from the rules, and their scope for search pre-filtering, from the
 * same parse.
 *
 * <p>Why not {@code AclAuthorizerService}: it resolves the imported classes with
 * {@code Class.forName} in its own class loader. Under Quarkus test and dev class loading, that
 * finds a second copy of Registry's resource types, which is not a {@code ResourceType} in its
 * view, so every rules file is rejected. Here classes are resolved in Registry's class loader and
 * passed to the public {@link AclAuthorizer.Builder}. Validation follows
 * {@code AclAuthorizerService} rule for rule; {@code AccessRulesTest} checks the two agree.</p>
 *
 * <p>The scope follows {@code AclAuthorizer}'s matching, so it selects exactly the names the
 * authorizer allows:</p>
 * <ul>
 *     <li>a rule applies to a subject if one of its principals has exactly the rule's principal
 *     class and its name matches ({@code =}, {@code in}, {@code like} prefix or {@code *});</li>
 *     <li>a rule's operations are expanded with the operations they imply, for allow and deny rules
 *     alike;</li>
 *     <li>deny rules take precedence over allow rules.</li>
 * </ul>
 *
 * <p>The scope is temporary: it is replaced by a resource scope API implemented upstream by
 * {@code AclAuthorizer} (apicurio-registry#10601).</p>
 */
final class AccessRules {

    /** The principal class the rules language uses for {@code User} when it is not imported. */
    private static final Map<String, String> DEFAULT_IMPORTS = Map.of("User",
            "io.kroxylicious.proxy.authentication.User");

    private static final int MAX_ERRORS_REPORTED = 100;

    private enum Match {
        ANY, EXACT, PREFIX, PATTERN
    }

    /** How a rule selects names: {@code values} are exact names, one prefix, or one regex. */
    private record NameSelector(Match match, Set<String> values) {

        boolean matches(String name) {
            return switch (match) {
                case ANY -> true;
                case EXACT -> values.contains(name);
                case PREFIX -> name.startsWith(values.iterator().next());
                case PATTERN -> throw new IllegalStateException("Principals have no pattern selector");
            };
        }
    }

    /**
     * One rule. {@code operations} are the operations as written; {@code impliedOperations} adds
     * the operations they imply, which is what the rule covers.
     */
    private record Rule(boolean deny, Class<? extends Principal> principalClass, NameSelector principals,
            Class<?> resourceType, Set<? extends ResourceType<?>> operations,
            Set<? extends ResourceType<?>> impliedOperations, NameSelector resources) {
    }

    private final List<Rule> rules;

    private AccessRules(List<Rule> rules) {
        this.rules = rules;
    }

    /**
     * @throws IllegalArgumentException if the content is not a valid rules file, with the same
     *         errors {@code AclAuthorizerService} reports
     */
    static AccessRules parse(String content) {
        List<String> errors = new ArrayList<>();
        AclRulesLexer lexer = new AclRulesLexer(CharStreams.fromString(content));
        AclRulesParser parser = new AclRulesParser(new CommonTokenStream(lexer));
        BaseErrorListener collect = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                    int charPositionInLine, String msg, RecognitionException e) {
                errors.add(line + ":" + charPositionInLine + ": " + (msg.endsWith(".") ? msg : msg + "."));
            }
        };
        lexer.removeErrorListeners();
        lexer.addErrorListener(collect);
        parser.removeErrorListeners();
        parser.addErrorListener(collect);
        AclRulesParser.RuleContext file = parser.rule_();
        throwIfAny("syntax errors", errors);

        Builder builder = new Builder(errors);
        for (AclRulesParser.ImportStmtContext importStmt : file.importStmt()) {
            builder.addImport(importStmt);
        }
        for (AclRulesParser.DenyRuleContext deny : file.denyRule()) {
            builder.addRule(true, deny.allowOrDenyRule());
        }
        for (AclRulesParser.AllowRuleContext allow : file.allowRule()) {
            builder.addRule(false, allow.allowOrDenyRule());
        }
        throwIfAny("errors", errors);
        return new AccessRules(List.copyOf(builder.rules));
    }

    private static void throwIfAny(String kind, List<String> errors) {
        if (!errors.isEmpty()) {
            StringBuilder message = new StringBuilder("Found ").append(errors.size()).append(' ').append(kind);
            errors.stream().limit(MAX_ERRORS_REPORTED).forEach(e -> message.append(System.lineSeparator()).append(e));
            throw new IllegalArgumentException(message.toString());
        }
    }

    /** Collects the rules, reporting errors as {@code AclAuthorizerService} does. */
    private static final class Builder {
        private final List<String> errors;
        private final Map<String, String> imports = new HashMap<>();
        private final List<Rule> rules = new ArrayList<>();

        Builder(List<String> errors) {
            this.errors = errors;
        }

        private void error(Token token, String message) {
            errors.add(token.getLine() + ":" + token.getCharPositionInLine() + ": " + message);
        }

        void addImport(AclRulesParser.ImportStmtContext importStmt) {
            String packageName = importStmt.packageName().qualIdent().ident().stream()
                    .map(RuleContext::getText).collect(Collectors.joining("."));
            for (AclRulesParser.ImportElementContext element : importStmt.importList().importElement()) {
                String localName = element.local != null ? element.local.getText() : element.name.getText();
                Token token = element.local != null ? element.local.start : element.name.start;
                String was = imports.put(localName, packageName + "." + element.name.getText());
                if (was != null) {
                    error(token, "Local name '%s' is already being used for class %s.".formatted(localName, was));
                }
            }
        }

        void addRule(boolean deny, AclRulesParser.AllowOrDenyRuleContext rule) {
            AclRulesParser.UserPatternContext user = rule.userPattern();
            AclRulesParser.IdentContext principalType = user.principalType().ident();
            Class<? extends Principal> principalClass = lookupClass(principalType.getText(), principalType.start,
                    Principal.class, "Principal");
            NameSelector principals = principalSelector(user.userNamePred());

            AclRulesParser.OperationPatternContext operation = rule.operationPattern();
            AclRulesParser.IdentContext resource = operation.resource().ident();
            @SuppressWarnings("rawtypes")
            Class<? extends ResourceType> resourceType = lookupClass(resource.getText(), resource.start,
                    ResourceType.class, "ResourceType");
            Set<? extends ResourceType<?>> operations = resourceType == null ? null
                    : operations(resourceType, operation.operations());
            NameSelector resources = resourceSelector(operation.resourceNamePred());

            if (principalClass != null && principals != null && operations != null && resources != null) {
                rules.add(new Rule(deny, principalClass, principals, resourceType, operations,
                        implied(operations), resources));
            }
        }

        private <T> Class<? extends T> lookupClass(String localName, Token token, Class<T> type, String desc) {
            String className = imports.getOrDefault(localName, DEFAULT_IMPORTS.get(localName));
            if (className == null) {
                error(token, "%s class with name '%s' has not been imported.".formatted(desc, localName));
                return null;
            }
            try {
                // Registry's class loader, which also sees the Kroxylicious API types
                Class<?> c = Class.forName(className, true, AccessRules.class.getClassLoader());
                if (!type.isAssignableFrom(c)) {
                    error(token, "%s class '%s' is not a subclass of %s.".formatted(desc, localName, type));
                    return null;
                }
                return c.asSubclass(type);
            } catch (ClassNotFoundException e) {
                error(token, "%s class '%s' was not found.".formatted(desc, className));
                return null;
            }
        }

        private NameSelector principalSelector(AclRulesParser.UserNamePredContext pred) {
            if (pred.nameAny() != null) {
                return new NameSelector(Match.ANY, Set.of());
            }
            if (pred.nameEq() != null) {
                return new NameSelector(Match.EXACT, Set.of(unquote(pred.nameEq().STRING())));
            }
            if (pred.nameIn() != null) {
                return new NameSelector(Match.EXACT, names(pred.nameIn()));
            }
            return like(pred.nameLike());
        }

        private NameSelector resourceSelector(AclRulesParser.ResourceNamePredContext pred) {
            if (pred.nameAny() != null) {
                return new NameSelector(Match.ANY, Set.of());
            }
            if (pred.nameEq() != null) {
                return new NameSelector(Match.EXACT, Set.of(unquote(pred.nameEq().STRING())));
            }
            if (pred.nameIn() != null) {
                return new NameSelector(Match.EXACT, names(pred.nameIn()));
            }
            if (pred.nameMatch() != null) {
                String regex = unquote(pred.nameMatch().REGEX());
                try {
                    Pattern.compile(regex);
                } catch (PatternSyntaxException e) {
                    error(pred.nameMatch().REGEX().getSymbol(), "Regex provided for '%s' operation is not valid: %s."
                            .formatted(pred.nameMatch().MATCHING().getText(), e.getMessage()));
                    return null;
                }
                return new NameSelector(Match.PATTERN, Set.of(regex));
            }
            return like(pred.nameLike());
        }

        /**
         * {@code like "abc*"} selects a prefix, {@code like "*"} everything and {@code like "abc"}
         * (no wildcard) the exact name. A backslash escapes the next character, so {@code \*} is a
         * literal star. A wildcard is only allowed at the end.
         */
        private NameSelector like(AclRulesParser.NameLikeContext nameLike) {
            String pattern = unwrap(nameLike.STRING());
            StringBuilder prefix = new StringBuilder(pattern.length());
            boolean wildcard = false;
            for (int i = 0; i < pattern.length(); i++) {
                char ch = pattern.charAt(i);
                if (ch == '\\' && i + 1 < pattern.length()) {
                    prefix.append(pattern.charAt(++i));
                } else if (ch == '*') {
                    wildcard = true;
                    if (i != pattern.length() - 1) {
                        error(nameLike.STRING().getSymbol(), "Wildcard '*' only supported as last character in 'like'.");
                    }
                } else {
                    prefix.append(ch);
                }
            }
            if (!wildcard) {
                return new NameSelector(Match.EXACT, Set.of(prefix.toString()));
            }
            if (prefix.isEmpty()) {
                return new NameSelector(Match.ANY, Set.of());
            }
            return new NameSelector(Match.PREFIX, Set.of(prefix.toString()));
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private Set<? extends ResourceType<?>> operations(Class<? extends ResourceType> resourceType,
                AclRulesParser.OperationsContext operations) {
            Class<Enum> enumType = (Class<Enum>) resourceType.asSubclass(Enum.class);
            if (operations.STAR() != null) {
                return EnumSet.allOf(enumType);
            }
            List<AclRulesParser.OperationContext> named = operations.operation() != null
                    ? List.of(operations.operation()) : operations.operationSet().operation();
            EnumSet result = EnumSet.noneOf(enumType);
            for (AclRulesParser.OperationContext op : named) {
                try {
                    result.add(Enum.valueOf(enumType, op.ident().getText()));
                } catch (IllegalArgumentException e) {
                    error(op.ident().start, "Operation '%s' is not an operation of %s."
                            .formatted(op.ident().getText(), resourceType.getSimpleName()));
                    return null;
                }
            }
            return result;
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Set<? extends ResourceType<?>> implied(Set<? extends ResourceType<?>> operations) {
        EnumSet expanded = EnumSet.copyOf((Set) operations);
        for (ResourceType<?> op : operations) {
            expanded.addAll(op.implies());
        }
        return expanded;
    }

    private static Set<String> names(AclRulesParser.NameInContext nameIn) {
        return nameIn.STRING().stream().map(AccessRules::unquote).collect(Collectors.toSet());
    }

    private static String unwrap(TerminalNode literal) {
        String text = literal.getText();
        return text.substring(1, text.length() - 1);
    }

    /** Removes the delimiters and the escaping backslashes, as {@code AclAuthorizerService} does. */
    private static String unquote(TerminalNode literal) {
        return unwrap(literal).replaceAll("\\\\(.)", "$1");
    }

    /** @return a Kroxylicious {@link AclAuthorizer} with these rules */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    Authorizer toAuthorizer() {
        AclAuthorizer.Builder builder = new AclAuthorizer.Builder();
        for (Rule rule : rules) {
            var principals = (rule.deny() ? builder.deny() : builder.allow())
                    .subjectsHavingPrincipal(rule.principalClass());
            var operations = switch (rule.principals().match()) {
                case ANY -> principals.withAnyName();
                case EXACT -> principals.withNameIn(rule.principals().values());
                case PREFIX -> principals.withNameStartingWith(rule.principals().values().iterator().next());
                case PATTERN -> throw new IllegalStateException("Principals have no pattern selector");
            };
            AclAuthorizer.ResourceBuilder resources = operations.operations((Set) rule.operations());
            NameSelector names = rule.resources();
            switch (names.match()) {
                case ANY -> resources.onAllResources();
                case EXACT -> resources.onResourcesWithNameIn(names.values());
                case PREFIX -> resources.onResourcesWithNameStartingWith(names.values().iterator().next());
                case PATTERN -> resources.onResourcesWithNameMatching(names.values().iterator().next());
            }
        }
        return builder.build();
    }

    /**
     * @return the names of {@code operation}'s resource type that the rules allow {@code subject}
     *         to perform {@code operation} on
     */
    SearchFilterData scope(Identity subject, ResourceType<?> operation) {
        Class<?> resourceType = ((Enum<?>) operation).getDeclaringClass();
        boolean allowAll = false;
        Set<String> allowedExact = new HashSet<>();
        Set<String> allowedPrefix = new HashSet<>();
        Set<String> allowedPatterns = new HashSet<>();
        Set<String> deniedExact = new HashSet<>();
        Set<String> deniedPrefix = new HashSet<>();
        Set<String> deniedPatterns = new HashSet<>();
        for (Rule rule : rules) {
            if (rule.resourceType() != resourceType || !rule.impliedOperations().contains(operation)
                    || !appliesTo(rule, subject)) {
                continue;
            }
            NameSelector resources = rule.resources();
            switch (resources.match()) {
                case ANY -> {
                    if (rule.deny()) {
                        deniedPrefix.add("");
                    } else {
                        allowAll = true;
                    }
                }
                case EXACT -> (rule.deny() ? deniedExact : allowedExact).addAll(resources.values());
                case PREFIX -> (rule.deny() ? deniedPrefix : allowedPrefix).addAll(resources.values());
                case PATTERN -> (rule.deny() ? deniedPatterns : allowedPatterns).addAll(resources.values());
            }
        }
        return new SearchFilterData(allowAll, allowedExact, allowedPrefix, allowedPatterns, deniedExact,
                deniedPrefix, deniedPatterns);
    }

    private static boolean appliesTo(Rule rule, Identity subject) {
        for (Principal principal : subject.principals()) {
            // Exact class, as AclAuthorizer keys rules by the principal's class
            if (principal.getClass() == rule.principalClass() && rule.principals().matches(principal.name())) {
                return true;
            }
        }
        return false;
    }
}
