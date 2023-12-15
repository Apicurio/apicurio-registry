package tests

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"

	registryclientv2 "github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2"
	"github.com/apicurio/apicurio-registry/go-sdk/pkg/registryclient-v2/models"
	auth "github.com/microsoft/kiota-abstractions-go/authentication"
	kiotaHttp "github.com/microsoft/kiota-http-go"
)

const RegistryHost = "localhost"
const RegistryPort = "8080"

var RegistryUrl = fmt.Sprintf("http://%s:%s/apis/registry/v2", RegistryHost, RegistryPort)

func setupSuite(t *testing.T) func(t *testing.T) {
	t.Log("Starting Registry")

	pwd, err := os.Getwd()
	assert.Nil(t, err)
	libRegEx, err := regexp.Compile(`^.+-runner.(jar)$`)
	assert.Nil(t, err)

	registryJar := "not-found"
	err = filepath.Walk(fmt.Sprintf("%s/../../../app/target/", pwd), func(path string, info os.FileInfo, err error) error {
		if err == nil && libRegEx.MatchString(info.Name()) {
			registryJar = info.Name()
		}
		return nil
	})
	assert.Nil(t, err)

	cmd := exec.Command("java", "-jar", fmt.Sprintf("%s/../../../app/target/%s", pwd, registryJar))
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	err = cmd.Start()
	assert.Nil(t, err)

	retries := 10
	for i := 0; i < retries; i++ {
		t.Log("Checking Registry is started ...")
		req, err := http.NewRequest("GET", RegistryUrl, nil)
		assert.Nil(t, err)

		resp, err := http.DefaultClient.Do(req)
		if err != nil {
			t.Log("Registry not started yet ...")
			time.Sleep(1 * time.Second)
		} else {
			defer resp.Body.Close()

			if resp.StatusCode == 200 {
				t.Log("Registry Started!")
				break
			} else {
				t.Log("Registry not started yet ...")
				time.Sleep(1 * time.Second)
			}
		}
	}

	return func(t *testing.T) {
		cmd.Process.Kill()
		t.Log("Killing Registry server")
	}
}

func TestAccessSystemInfo(t *testing.T) {
	teardownSuite := setupSuite(t)
	defer teardownSuite(t)

	authProvider := auth.AnonymousAuthenticationProvider{}

	adapter, err := kiotaHttp.NewNetHttpRequestAdapter(&authProvider)
	adapter.SetBaseUrl(RegistryUrl)
	assert.Nil(t, err)
	client := registryclientv2.NewApiClient(adapter)

	info, err := client.System().Info().Get(context.Background(), nil)
	assert.Nil(t, err)

	assert.Equal(t, "apicurio-registry", *info.GetName())
}

func TestCreateAnArtifact(t *testing.T) {
	teardownSuite := setupSuite(t)
	defer teardownSuite(t)

	authProvider := auth.AnonymousAuthenticationProvider{}

	// Workaround for: https://github.com/microsoft/kiota-http-go/issues/130
	httpClient := kiotaHttp.GetDefaultClient(
		kiotaHttp.NewRetryHandler(),
		kiotaHttp.NewRedirectHandler(),
		kiotaHttp.NewParametersNameDecodingHandler(),
		// NewCompressionHandler(),
		kiotaHttp.NewUserAgentHandler(),
		kiotaHttp.NewHeadersInspectionHandler(),
	)

	adapter, err := kiotaHttp.NewNetHttpRequestAdapterWithParseNodeFactoryAndSerializationWriterFactoryAndHttpClient(&authProvider, nil, nil, httpClient)
	// adapter, err := kiotaHttp.NewNetHttpRequestAdapter(&authProvider)
	adapter.SetBaseUrl(RegistryUrl)
	assert.Nil(t, err)
	client := registryclientv2.NewApiClient(adapter)
	contentStr := `{ "openapi": "3.0.0", "info": { "title": "My API", "version": "1.0.0" }, "paths": {} }`
	content := models.NewArtifactContent()
	content.SetContent(&contentStr)

	artifact, err := client.Groups().ByGroupId("default").Artifacts().Post(context.Background(), content, nil)
	assert.Nil(t, err)

	resultArtifact, err := client.Groups().ByGroupId("default").Artifacts().ByArtifactId(*artifact.GetId()).Get(context.Background(), nil)
	assert.Nil(t, err)

	assert.Equal(t, contentStr, string(resultArtifact[:]))
}
