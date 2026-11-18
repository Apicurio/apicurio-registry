module github.com/apicurio/apicurio-registry/examples/registry-clients/go-sdk

go 1.25.0

require (
	github.com/apicurio/apicurio-registry/go-sdk/v3 v3.0.0
	github.com/microsoft/kiota-abstractions-go v1.11.1
	github.com/microsoft/kiota-http-go v1.5.6
)

require (
	github.com/cespare/xxhash/v2 v2.3.0 // indirect
	github.com/go-logr/logr v1.4.4 // indirect
	github.com/go-logr/stdr v1.2.2 // indirect
	github.com/google/uuid v1.6.0 // indirect
	github.com/microsoft/kiota-serialization-form-go v1.1.3 // indirect
	github.com/microsoft/kiota-serialization-json-go v1.1.4 // indirect
	github.com/microsoft/kiota-serialization-multipart-go v1.1.2 // indirect
	github.com/microsoft/kiota-serialization-text-go v1.1.3 // indirect
	github.com/std-uritemplate/std-uritemplate/go/v2 v2.0.12 // indirect
	go.opentelemetry.io/auto/sdk v1.2.1 // indirect
	go.opentelemetry.io/otel v1.46.0 // indirect
	go.opentelemetry.io/otel/metric v1.46.0 // indirect
	go.opentelemetry.io/otel/trace v1.46.0 // indirect
)

// Use the Go SDK of this checkout, not a released one
replace github.com/apicurio/apicurio-registry/go-sdk/v3 => ../../../go-sdk
