package models
// Current task state: `pending` (queued, waiting for capacity), `submitted` (request sent to sidecar), `fetching` (sidecar is cloning), `validating` (registry is loading and validating), `completed` (finished with results), `failed` (an error occurred).
type GitOpsValidateTask_state int

const (
    PENDING_GITOPSVALIDATETASK_STATE GitOpsValidateTask_state = iota
    SUBMITTED_GITOPSVALIDATETASK_STATE
    FETCHING_GITOPSVALIDATETASK_STATE
    VALIDATING_GITOPSVALIDATETASK_STATE
    COMPLETED_GITOPSVALIDATETASK_STATE
    FAILED_GITOPSVALIDATETASK_STATE
)

func (i GitOpsValidateTask_state) String() string {
    return []string{"pending", "submitted", "fetching", "validating", "completed", "failed"}[i]
}
func ParseGitOpsValidateTask_state(v string) (any, error) {
    result := PENDING_GITOPSVALIDATETASK_STATE
    switch v {
        case "pending":
            result = PENDING_GITOPSVALIDATETASK_STATE
        case "submitted":
            result = SUBMITTED_GITOPSVALIDATETASK_STATE
        case "fetching":
            result = FETCHING_GITOPSVALIDATETASK_STATE
        case "validating":
            result = VALIDATING_GITOPSVALIDATETASK_STATE
        case "completed":
            result = COMPLETED_GITOPSVALIDATETASK_STATE
        case "failed":
            result = FAILED_GITOPSVALIDATETASK_STATE
        default:
            return nil, nil
    }
    return &result, nil
}
func SerializeGitOpsValidateTask_state(values []GitOpsValidateTask_state) []string {
    result := make([]string, len(values))
    for i, v := range values {
        result[i] = v.String()
    }
    return result
}
func (i GitOpsValidateTask_state) isMultiValue() bool {
    return false
}
