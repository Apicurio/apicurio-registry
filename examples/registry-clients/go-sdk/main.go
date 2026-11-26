// The registry-clients scenario with the Go SDK. Prints one line per step; demo.sh compares them
// with the other clients'.
//
// Usage: go run . <registry v3 URL> <schemas directory>
package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	registry "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3"
	"github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	"github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/search"
	abs "github.com/microsoft/kiota-abstractions-go"
	"github.com/microsoft/kiota-abstractions-go/authentication"
	kiotaHttp "github.com/microsoft/kiota-http-go"
)

const (
	group    = "clients"
	artifact = "go-sdk-order"
)

func main() {
	if len(os.Args) != 3 {
		fmt.Fprintln(os.Stderr, "usage: go run . <registry v3 URL> <schemas directory>")
		os.Exit(2)
	}
	adapter, err := kiotaHttp.NewNetHttpRequestAdapter(&authentication.AnonymousAuthenticationProvider{})
	check(err)
	adapter.SetBaseUrl(os.Args[1])
	client := registry.NewApiClient(adapter)
	schemas := os.Args[2]
	ctx := context.Background()

	// 1. Server information
	info, err := client.System().Info().Get(ctx, nil)
	check(err)
	if info.GetVersion() != nil && *info.GetVersion() != "" {
		fmt.Println("info ok")
	} else {
		fmt.Println("info empty")
	}

	// 2. Create the artifact with its first version
	createArtifact := models.NewCreateArtifact()
	createArtifact.SetArtifactId(ptr(artifact))
	createArtifact.SetArtifactType(ptr("AVRO"))
	createArtifact.SetFirstVersion(version(filepath.Join(schemas, "order-v1.avsc")))
	created, err := client.Groups().ByGroupId(group).Artifacts().Post(ctx, createArtifact, nil)
	check(err)
	fmt.Println("created", *created.GetVersion().GetVersion())

	// 3. BACKWARD compatibility rule on the artifact
	art := client.Groups().ByGroupId(group).Artifacts().ByArtifactId(artifact)
	rule := models.NewCreateRule()
	ruleType := models.COMPATIBILITY_RULETYPE
	rule.SetRuleType(&ruleType)
	rule.SetConfig(ptr("BACKWARD"))
	check(art.Rules().Post(ctx, rule, nil))
	got, err := art.Rules().ByRuleType(ruleType.String()).Get(ctx, nil)
	check(err)
	fmt.Println("rule", *got.GetConfig())

	// 4. A compatible version: a new field with a default
	v2, err := art.Versions().Post(ctx, version(filepath.Join(schemas, "order-v2.avsc")), nil)
	check(err)
	fmt.Println("version", *v2.GetVersion())

	// 5. An incompatible version: a new field without a default, rejected by the rule
	_, err = art.Versions().Post(ctx, version(filepath.Join(schemas, "order-v3-incompatible.avsc")), nil)
	fmt.Println("rejected", status(err))

	// 6. The latest version and its content
	latest, err := art.Versions().ByVersionExpression("branch=latest").Get(ctx, nil)
	check(err)
	fmt.Println("latest", *latest.GetVersion())
	content, err := art.Versions().ByVersionExpression("branch=latest").Content().Get(ctx, nil)
	check(err)
	fmt.Println("fields", fields(content))

	// 7. Search
	found, err := client.Search().Artifacts().Get(ctx, &search.ArtifactsRequestBuilderGetRequestConfiguration{
		QueryParameters: &search.ArtifactsRequestBuilderGetQueryParameters{GroupId: ptr(group), ArtifactId: ptr(artifact)},
	})
	check(err)
	fmt.Println("search", *found.GetCount())

	// 8. Delete, then read it again
	check(art.Delete(ctx, nil))
	fmt.Println("deleted")
	_, err = art.Get(ctx, nil)
	fmt.Println("missing", status(err))
}

func version(schema string) models.CreateVersionable {
	data, err := os.ReadFile(schema)
	check(err)
	content := models.NewVersionContent()
	content.SetContent(ptr(string(data)))
	content.SetContentType(ptr("application/json"))
	v := models.NewCreateVersion()
	v.SetContent(content)
	return v
}

// status is the HTTP status of a failed request, or "none" if it succeeded
func status(err error) string {
	if err == nil {
		return "none"
	}
	var apiErr abs.ApiErrorable
	if errors.As(err, &apiErr) {
		return fmt.Sprint(apiErr.GetStatusCode())
	}
	return "error: " + err.Error()
}

func fields(avroSchema []byte) string {
	var schema struct {
		Fields []struct{ Name string } `json:"fields"`
	}
	check(json.Unmarshal(avroSchema, &schema))
	names := make([]string, 0, len(schema.Fields))
	for _, f := range schema.Fields {
		names = append(names, f.Name)
	}
	return strings.Join(names, ",")
}

func check(err error) {
	if err != nil {
		fmt.Fprintln(os.Stderr, "error:", err)
		os.Exit(1)
	}
}

func ptr[T any](v T) *T { return &v }
