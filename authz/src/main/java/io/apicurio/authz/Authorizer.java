package io.apicurio.authz;

import java.util.List;
import java.util.concurrent.CompletionStage;

public interface Authorizer {

    CompletionStage<AuthorizeResult> authorize(Subject subject, List<Action> actions);
}
