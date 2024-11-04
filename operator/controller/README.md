# Operator Controller

This is the module containing the actual reconciliation logic, and this README is specific to that. For more general information about the operator, see `../README.md`.

## PodTemplateSpec merging

Users can set the `spec.(app/ui).podTemplateSpec` fields in `ApicurioRegistry3` CR to greatly customize Apicurio registry Kubernetes deployment. The value of these fields is merged with values set by the operator and used in the resulting `Deployment` resources for the `app` or the `ui` components. In general, the `podTemplateSpec` field
is used as the base, and default values (as described below) are merged with it. Then any additional features that the operator provides (represented by other fields in the CR, such as persistence configuration) are applied on top. This means, for example, that the `spec.(app/ui).podTemplateSpec.spec.containers[name = ...].env` field has a lower priority than the `spec.(app/ui).env` field, and therefore the environment variables must be set using the `spec.(app/ui).env` field instead.

- `spec.(app/ui).podTemplateSpec.metadata.(labels/annotations)` user can add labels and annotations, but entries set by the operator cannot be overridden.
- `spec.(app/ui).podTemplateSpec.spec.containers` user can add containers, but to modify containers of the Registry components, container names `apicurio-registry-app` or `apicurio-registry-ui` must be used.
- `spec.(app/ui).podTemplateSpec.spec.containers[name = apicurio-registry-app/apicurio-registry-ui].image` user can override the default image (not recommended)
- `spec.(app/ui).podTemplateSpec.spec.containers[name = apicurio-registry-app/apicurio-registry-ui].env` this field must not be used. Use `spec.(app/ui).env` field instead.
- `spec.(app/ui).podTemplateSpec.spec.containers[name = apicurio-registry-app/apicurio-registry-ui].ports` user can add or override the default ports configuration. Ports are matched by name.
- `spec.(app/ui).podTemplateSpec.spec.containers[name = apicurio-registry-app/apicurio-registry-ui].readinessProbe` user override this field as a whole (subfields are not merged).
- `spec.(app/ui).podTemplateSpec.spec.containers[name = apicurio-registry-app/apicurio-registry-ui].livenessProbe` user override this field as a whole (subfields are not merged).
- `spec.(app/ui).podTemplateSpec.spec.containers[name = apicurio-registry-app/apicurio-registry-ui].resources` user override this field as a whole (subfields are not merged).
