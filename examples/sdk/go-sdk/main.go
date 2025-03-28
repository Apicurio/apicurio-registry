package main

import (
	"context"
	"flag"
	"fmt"
	// 3.0.6:
	registry3 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3"
	"github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v3/models"
	// 3.0.7 and later:
	// registry3 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3"
	// "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	kiotaAuth "github.com/microsoft/kiota-abstractions-go/authentication"
	kiotaHttp "github.com/microsoft/kiota-http-go"
	"regexp"
)

func initClient(registryUrl *string) *registry3.ApiClient {
	prefix, err := regexp.Compile("^https?://.*")
	if err != nil {
		panic("Unexpected error: " + err.Error())
	}
	if !prefix.MatchString(*registryUrl) {
		s := "http://" + *registryUrl
		registryUrl = &s
	}
	suffix, err := regexp.Compile(".*/apis/registry/v3/?")
	if err != nil {
		panic("Unexpected error: " + err.Error())
	}
	if !suffix.MatchString(*registryUrl) {
		s := *registryUrl + "/apis/registry/v3"
		registryUrl = &s
	}

	// Compression is not currently supported by the Apicurio Registry server.
	httpClient := kiotaHttp.GetDefaultClient(
		kiotaHttp.NewRetryHandler(),
		kiotaHttp.NewRedirectHandler(),
		kiotaHttp.NewParametersNameDecodingHandler(),
		kiotaHttp.NewCompressionHandlerWithOptions(kiotaHttp.NewCompressionOptions(false)),
		kiotaHttp.NewUserAgentHandler(),
		kiotaHttp.NewHeadersInspectionHandler(),
	)

	/*
		middleware, err := kiotaHttp.GetDefaultMiddlewaresWithOptions(kiotaHttp.NewCompressionOptions(false))
		if err != nil {
			// panic: Unexpected error: unsupported option type
			panic("Unexpected error: " + err.Error())
		}
		httpClient := kiotaHttp.GetDefaultClient(middleware...)
	*/

	adapter, err := kiotaHttp.NewNetHttpRequestAdapterWithParseNodeFactoryAndSerializationWriterFactoryAndHttpClient(
		&kiotaAuth.AnonymousAuthenticationProvider{}, nil, nil, httpClient,
	)
	if err != nil {
		panic("Unexpected error: " + err.Error())
	}
	adapter.SetBaseUrl(*registryUrl)

	return registry3.NewApiClient(adapter)
}

func getServerInfo(client *registry3.ApiClient) {
	res, err := client.System().Info().Get(context.Background(), nil)
	if res != nil {
		fmt.Printf("Server name: %s\n", *res.GetName())
		fmt.Printf("Server version: %s\n", *res.GetVersion())
	} else {
		handleError(err)
	}
}

func createNewArtifactVersion(client *registry3.ApiClient) {
	newContent := models.NewVersionContent()
	content := `{"openapi": "3.0.0", "info": {"title": "My API", "version": "1.0.0"}, "paths": {}}`
	newContent.SetContent(&content)
	contentType := "application/json"
	newContent.SetContentType(&contentType)

	newVersion := models.NewCreateVersion()
	newVersion.SetContent(newContent)

	newArtifact := models.NewCreateArtifact()
	newArtifact.SetFirstVersion(newVersion)

	res, err := client.Groups().ByGroupId("default").Artifacts().Post(context.Background(), newArtifact, nil)
	if res != nil {
		fmt.Printf("Created version %s of artifact %s\n", *res.GetVersion().GetVersion(), *res.GetVersion().GetArtifactId())
	} else {
		handleError(err)
	}
}

func handleError(err error) {
	if err == nil {
		panic("No error value")
	}
	if details, ok := err.(*models.ProblemDetails); ok {
		fmt.Printf("Server error: %s\n", *details.GetDetail())
	} else {
		fmt.Printf("Unknown error: %s\n", err.Error())
	}
}

func main() {
	registryUrl := flag.String("url", "http://localhost:8080", "URL of the Apicurio Registry 3 server")
	flag.Parse()
	client := initClient(registryUrl)
	getServerInfo(client)
	createNewArtifactVersion(client)
}
