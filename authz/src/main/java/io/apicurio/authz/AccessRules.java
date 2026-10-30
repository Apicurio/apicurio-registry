package io.apicurio.authz;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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

import io.apicurio.scope.ResourceScope;
import io.kroxylicious.authorizer.provider.acl.parser.AclRulesLexer;
import io.kroxylicious.authorizer.provider.acl.parser.AclRulesParser;
import io.kroxylicious.authorizer.service.Action;
import io.kroxylicious.authorizer.service.AuthorizeResult;
import io.kroxylicious.authorizer.service.Authorizer;
import io.kroxylicious.authorizer.service.ResourceType;
import io.kroxylicious.identity.Identity;
import io.kroxylicious.identity.Principal;

/**
 * An access rules file in the Kroxylicious ACL rules format, parsed with the Kroxylicious rules
 * parser ({@code AclRulesParser}). It provides both the point decisions ({@link #authorizer()})
 * and their scope for search pre-filtering ({@link #scope}), from one parse and one matching
 * logic, so search always selects exactly what point access allows.
 *
 * <p>Matching follows the semantics of Kroxylicious proposal 009:</p>
 * <ul>
 *     <li>a rule applies to a subject if one of its principals has exactly the rule's principal
 *     class and its name matches ({@code =}, {@code in}, {@code like} prefix or {@code *});</li>
 *     <li>a rule applies to a resource name if the name matches ({@code =}, {@code in},
 *     {@code like} prefix, {@code *}, or a full RE2 match of {@code matching});</li>
 *     <li>a rule's operations are expanded with the operations they imply, for allow and deny rules
 *     alike;</li>
 *     <li>a matching deny rule takes precedence over every allow rule; without a matching allow
 *     rule, access is denied.</li>
 * </ul>
 *
 * <p>Why not the Kroxylicious {@code AclAuthorizer}:</p>
 * <ul>
 *     <li>{@code AclAuthorizerService} resolves imported classes with {@code Class.forName} in its
 *     own class loader. Under Quarkus test and dev class loading that finds a second copy of
 *     Registry's resource types, so every rules file is rejected. Here classes are resolved in
 *     Registry's class loader; validation follows {@code AclAuthorizerService} rule for rule.</li>
 *     <li>{@code AclAuthorizer} 0.25.1 has a prefix-matching defect (reported to Kroxylicious), so
 *     decisions are evaluated here. {@code AccessRulesTest} checks that they otherwise agree with
 *     {@code AclAuthorizerService}.</li>
 * </ul>
 *
 */
final class AccessRules {

    /** The principal class the rules language uses for {@code User} when it is not imported. */
    private static final Map<String, String> DEFAULT_IMPORTS = Map.of("User",
            "io.kroxylicious.proxy.authentication.User");

    private static final int MAX_ERRORS_REPORTED = 100;

    private enum Match {
        ANY, EXACT, PREFIX, PATTERN
    }

    /**
     * How a rule selects names: {@code values} are exact names, one prefix, or one regex, compiled
     * in {@code pattern}.
     */
    private record NameSelector(Match match, Set<String> values, Pattern pattern) {

        NameSelector(Match match, Set<String> values) {
            this(match, values, match == Match.PATTERN ? Pattern.compile(values.iterator().next()) : null);
        }

        boolean matches(String name) {
            return switch (match) {
                case ANY -> true;
                case EXACT -> values.contains(name);
                case PREFIX -> name.startsWith(values.iterator().next());
                case PATTERN -> pattern.matches(name);
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

    /**
     * @return whether the rules allow {@code subject} to perform {@code operation} on the resource
     *         named {@code resourceName}
     */
    boolean allows(Identity subject, ResourceType<?> operation, String resourceName) {
        Class<?> resourceType = ((Enum<?>) operation).getDeclaringClass();
        boolean allowed = false;
        for (Rule rule : rules) {
            if (rule.resourceType() == resourceType && rule.impliedOperations().contains(operation)
                    && rule.resources().matches(resourceName) && appliesTo(rule, subject)) {
                if (rule.deny()) {
                    return false;
                }
                allowed = true;
            }
        }
        return allowed;
    }

    /** @return the resource types these rules refer to */
    Set<Class<? extends ResourceType<?>>> resourceTypes() {
        Set<Class<? extends ResourceType<?>>> types = new HashSet<>();
        for (Rule rule : rules) {
            @SuppressWarnings("unchecked")
            Class<? extends ResourceType<?>> type = (Class<? extends ResourceType<?>>) rule.resourceType();
            types.add(type);
        }
        return types;
    }

    /** @return a Kroxylicious {@link Authorizer} deciding with these rules ({@link #allows}) */
    Authorizer authorizer() {
        Set<Class<? extends ResourceType<?>>> types = Set.copyOf(resourceTypes());
        return new Authorizer() {
            @Override
            public CompletionStage<AuthorizeResult> authorize(Identity subject, List<Action> actions) {
                List<Action> allowed = new ArrayList<>();
                List<Action> denied = new ArrayList<>();
                for (Action action : actions) {
                    (allows(subject, action.operation(), action.resourceName()) ? allowed : denied).add(action);
                }
                return CompletableFuture.completedStage(new AuthorizeResult(subject, allowed, denied));
            }

            @Override
            public Optional<Set<Class<? extends ResourceType<?>>>> supportedResourceTypes() {
                return Optional.of(types);
            }
        };
    }

    /**
     * @return the names of {@code operation}'s resource type that the rules allow {@code subject}
     *         to perform {@code operation} on
     */
    ResourceScope scope(Identity subject, ResourceType<?> operation) {
        Class<?> resourceType = ((Enum<?>) operation).getDeclaringClass();
        Selectors allowed = new Selectors();
        Selectors denied = new Selectors();
        for (Rule rule : rules) {
            if (rule.resourceType() == resourceType && rule.impliedOperations().contains(operation)
                    && appliesTo(rule, subject)) {
                (rule.deny() ? denied : allowed).add(rule.resources());
            }
        }
        // A deny of every name is the empty deny prefix
        if (denied.all) {
            denied.prefix.add("");
        }
        return ResourceScope.of(allowed.all, allowed.exact, allowed.prefix, allowed.patterns, denied.exact,
                denied.prefix, denied.patterns);
    }

    /** The resource selectors of the allow or of the deny rules that apply. */
    private static final class Selectors {
        private boolean all;
        private final Set<String> exact = new HashSet<>();
        private final Set<String> prefix = new HashSet<>();
        private final Set<String> patterns = new HashSet<>();

        void add(NameSelector selector) {
            switch (selector.match()) {
                case ANY -> all = true;
                case EXACT -> exact.addAll(selector.values());
                case PREFIX -> prefix.addAll(selector.values());
                case PATTERN -> patterns.addAll(selector.values());
            }
        }
    }

    private static boolean appliesTo(Rule rule, Identity subject) {
        for (Principal principal : subject.principals()) {
            // Exact class: a Role rule must not match a User of the same name
            if (principal.getClass() == rule.principalClass() && rule.principals().matches(principal.name())) {
                return true;
            }
        }
        return false;
    }
}
