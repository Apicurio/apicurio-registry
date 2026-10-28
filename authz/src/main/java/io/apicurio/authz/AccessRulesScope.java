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
import org.antlr.v4.runtime.tree.TerminalNode;

import io.kroxylicious.authorizer.provider.acl.parser.AclRulesLexer;
import io.kroxylicious.authorizer.provider.acl.parser.AclRulesParser;
import io.kroxylicious.authorizer.service.ResourceType;
import io.kroxylicious.identity.Identity;
import io.kroxylicious.identity.Principal;

/**
 * The scope of an access rules file: for a subject and an operation, the resource names the
 * rules allow and deny, as a {@link SearchFilterData} that search can translate into a query.
 *
 * <p>The Kroxylicious {@code AclAuthorizer} answers point decisions only. This class reads the
 * same rules with the same parser and follows the same matching, so the scope selects exactly the
 * names {@code AclAuthorizer} allows:</p>
 * <ul>
 *     <li>a rule applies to a subject if one of its principals has exactly the rule's principal
 *     class and its name matches ({@code =}, {@code in}, {@code like} prefix or {@code *});</li>
 *     <li>a rule's operations are expanded with the operations they imply, for allow and deny rules
 *     alike;</li>
 *     <li>deny rules take precedence over allow rules.</li>
 * </ul>
 *
 * <p>Only parse files that {@code AclAuthorizer} has already accepted: this class does not
 * validate them.</p>
 *
 * <p>Temporary: this is replaced by a resource scope API implemented upstream by
 * {@code AclAuthorizer} (apicurio-registry#10601).</p>
 */
final class AccessRulesScope {

    /** The principal class the rules language uses for {@code User} when it is not imported. */
    private static final Map<String, String> DEFAULT_IMPORTS = Map.of("User",
            "io.kroxylicious.proxy.authentication.User");

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

    private record Rule(boolean deny, String principalClass, NameSelector principals,
            Class<?> resourceType, Set<? extends ResourceType<?>> operations, NameSelector resources) {
    }

    private final List<Rule> rules;

    private AccessRulesScope(List<Rule> rules) {
        this.rules = rules;
    }

    /**
     * @throws IllegalArgumentException if the content is not a rules file
     */
    static AccessRulesScope parse(String content) {
        AclRulesLexer lexer = new AclRulesLexer(CharStreams.fromString(content));
        AclRulesParser parser = new AclRulesParser(new CommonTokenStream(lexer));
        BaseErrorListener failOnError = new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                    int charPositionInLine, String msg, RecognitionException e) {
                throw new IllegalArgumentException(line + ":" + charPositionInLine + ": " + msg);
            }
        };
        lexer.removeErrorListeners();
        lexer.addErrorListener(failOnError);
        parser.removeErrorListeners();
        parser.addErrorListener(failOnError);
        AclRulesParser.RuleContext file = parser.rule_();

        Map<String, String> imports = new HashMap<>(DEFAULT_IMPORTS);
        for (AclRulesParser.ImportStmtContext importStmt : file.importStmt()) {
            String packageName = importStmt.packageName().qualIdent().ident().stream()
                    .map(RuleContext::getText).collect(Collectors.joining("."));
            for (AclRulesParser.ImportElementContext element : importStmt.importList().importElement()) {
                String localName = element.local != null ? element.local.getText() : element.name.getText();
                imports.put(localName, packageName + "." + element.name.getText());
            }
        }
        List<Rule> rules = new ArrayList<>();
        for (AclRulesParser.DenyRuleContext deny : file.denyRule()) {
            rules.add(rule(true, deny.allowOrDenyRule(), imports));
        }
        for (AclRulesParser.AllowRuleContext allow : file.allowRule()) {
            rules.add(rule(false, allow.allowOrDenyRule(), imports));
        }
        return new AccessRulesScope(List.copyOf(rules));
    }

    private static Rule rule(boolean deny, AclRulesParser.AllowOrDenyRuleContext rule, Map<String, String> imports) {
        AclRulesParser.UserPatternContext user = rule.userPattern();
        String principalClass = imports.get(user.principalType().ident().getText());
        NameSelector principals = principalSelector(user.userNamePred());

        AclRulesParser.OperationPatternContext operation = rule.operationPattern();
        Class<?> resourceType = resourceType(imports.get(operation.resource().ident().getText()));
        Set<? extends ResourceType<?>> operations = operations(resourceType, operation.operations());
        NameSelector resources = resourceSelector(operation.resourceNamePred());
        return new Rule(deny, principalClass, principals, resourceType, operations, resources);
    }

    private static NameSelector principalSelector(AclRulesParser.UserNamePredContext pred) {
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

    private static NameSelector resourceSelector(AclRulesParser.ResourceNamePredContext pred) {
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
            return new NameSelector(Match.PATTERN, Set.of(unquote(pred.nameMatch().REGEX())));
        }
        return like(pred.nameLike());
    }

    private static Set<String> names(AclRulesParser.NameInContext nameIn) {
        return nameIn.STRING().stream().map(AccessRulesScope::unquote).collect(Collectors.toSet());
    }

    /**
     * {@code like "abc*"} selects a prefix, {@code like "*"} everything and {@code like "abc"} (no
     * wildcard) the exact name. A backslash escapes the next character, so {@code \*} is a literal
     * star. As in {@code AclAuthorizer}, a star is only allowed at the end.
     */
    private static NameSelector like(AclRulesParser.NameLikeContext nameLike) {
        String pattern = unwrap(nameLike.STRING());
        StringBuilder prefix = new StringBuilder(pattern.length());
        boolean wildcard = false;
        for (int i = 0; i < pattern.length(); i++) {
            char ch = pattern.charAt(i);
            if (ch == '\\' && i + 1 < pattern.length()) {
                prefix.append(pattern.charAt(++i));
            } else if (ch == '*') {
                wildcard = true;
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

    private static String unwrap(TerminalNode literal) {
        String text = literal.getText();
        return text.substring(1, text.length() - 1);
    }

    /** Removes the delimiters and the escaping backslashes, as {@code AclAuthorizer} does. */
    private static String unquote(TerminalNode literal) {
        return unwrap(literal).replaceAll("\\\\(.)", "$1");
    }

    private static Class<?> resourceType(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Resource type class not found: " + className, e);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Set<? extends ResourceType<?>> operations(Class<?> resourceType,
            AclRulesParser.OperationsContext operations) {
        Class<Enum> enumType = (Class<Enum>) resourceType.asSubclass(Enum.class);
        EnumSet expanded;
        if (operations.STAR() != null) {
            expanded = EnumSet.allOf(enumType);
        } else {
            List<AclRulesParser.OperationContext> named = operations.operation() != null
                    ? List.of(operations.operation()) : operations.operationSet().operation();
            expanded = EnumSet.noneOf(enumType);
            for (AclRulesParser.OperationContext op : named) {
                expanded.add(Enum.valueOf(enumType, op.ident().getText()));
            }
        }
        for (Object op : EnumSet.copyOf(expanded)) {
            expanded.addAll(((ResourceType<?>) op).implies());
        }
        return expanded;
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
            if (rule.resourceType() != resourceType || !rule.operations().contains(operation)
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
            if (principal.getClass().getName().equals(rule.principalClass())
                    && rule.principals().matches(principal.name())) {
                return true;
            }
        }
        return false;
    }
}
