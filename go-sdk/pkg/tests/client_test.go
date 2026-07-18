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

	registryclientv3 "github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3"
	"github.com/apicurio/apicurio-registry/go-sdk/v3/pkg/registryclient-v3/models"
	auth "github.com/microsoft/kiota-abstractions-go/authentication"
	kiotaHttp "github.com/microsoft/kiota-http-go"
)

const RegistryHost = "localhost"
const RegistryPort = "8080"

var RegistryUrl = fmt.Sprintf("http://%s:%s/apis/registry/v3", RegistryHost, RegistryPort)

func setupSuite(t *testing.T) func(t *testing.T) {
	httpClient := &http.Client{Timeout: 2 * time.Second}
	resp, err := httpClient.Get(RegistryUrl)
	if err == nil {
		resp.Body.Close()
		if resp.StatusCode == 200 {
			t.Log("Registry already running")
			return func(t *testing.T) {
				t.Log("Registry is already running, skipping teardown")
			}
		}
	}

	t.Log("Starting Registry")
	if _, err := exec.LookPath("java"); err != nil {
		t.Fatalf("Java is not installed or not in PATH")
	}
	pwd, err := os.Getwd()
	if err != nil {
		t.Fatalf("failed to get working directory: %v", err)
	}

	targetDir := filepath.Join(pwd, "..", "..", "..", "app", "target")

	// 1. Check for Quarkus 3 layout first
	registryJarPath := filepath.Join(targetDir, "quarkus-app", "quarkus-run.jar")
	if _, err := os.Stat(registryJarPath); os.IsNotExist(err) {

		// 2. Fallback to Quarkus 2 layout (*-runner.jar)
		registryJarPath = ""
		libRegEx := regexp.MustCompile(`^.+-runner\.jar$`)
		err := filepath.Walk(targetDir, func(path string, info os.FileInfo, err error) error {
			if err != nil {
				return err
			}
			if !info.IsDir() && libRegEx.MatchString(info.Name()) {
				registryJarPath = path
			}
			return nil
		})
		if err != nil && !os.IsNotExist(err) {
			t.Fatalf("Failed to search for registry jar: %v", err)
		}
	}

	if registryJarPath == "" {
		t.Fatalf("Could not find registry runner jar. Did you build the java project?")
	}

	cmd := exec.Command("java", "-jar", registryJarPath)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	err = cmd.Start()
	if err != nil {
		t.Fatalf("failed to start registry: %v", err)
	}

	exitChan := make(chan error, 1)
	// This goroutine lives for the entire test suite to monitor the process lifecycle.
	go func() {
		exitChan <- cmd.Wait()
	}()

	ctx, cancel := context.WithTimeout(context.Background(), 250*time.Millisecond)
	defer cancel()

	// Wait a split second to see if Java instantly crashes (e.g. bad jar)
	select {
	case <-ctx.Done():
		// Still running, all good
	case <-exitChan:
		t.Fatalf("Registry process exited immediately with an error. Is the jar valid?")
	}

	started := false
	retries := 10
	//2-second HTTP timeout means this could wait up to 20 seconds total.
	for i := 0; i < retries; i++ {
		t.Log("Checking Registry is started ...")
		req, err := http.NewRequest("GET", RegistryUrl, nil)
		if err != nil {
			t.Fatalf("failed to create request: %v", err)
		}

		resp, err := httpClient.Do(req)
		if err != nil {
			t.Log("Registry not started yet ...")
			time.Sleep(1 * time.Second)
		} else {
			if resp.StatusCode == 200 {
				resp.Body.Close()
				t.Log("Registry Started!")
				started = true
				break
			} else {
				resp.Body.Close()
				t.Log("Registry not started yet ...")
				time.Sleep(1 * time.Second)
			}
		}
	}

	if !started {
		cmd.Process.Kill()
		t.Fatalf("Registry failed to start after 10 seconds")
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
	client := registryclientv3.NewApiClient(adapter)

	info, err := client.System().Info().Get(context.Background(), nil)
	assert.Nil(t, err)

	assert.Equal(t, "Apicurio Registry (SQL)", *info.GetName())
}

func TestCreateAnArtifact(t *testing.T) {
	teardownSuite := setupSuite(t)
	defer teardownSuite(t)

	authProvider := auth.AnonymousAuthenticationProvider{}

	// Disabling the compression handler, workaround for: https://github.com/microsoft/kiota-http-go/issues/130
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
	client := registryclientv3.NewApiClient(adapter)
	contentStr := `{ "openapi": "3.0.0", "info": { "title": "My API", "version": "1.0.0" }, "paths": {} }`
	contentType := "application/json"
	createArtifactReq := models.NewCreateArtifact()
	createArtifactReq.SetFirstVersion(models.NewCreateVersion())
	createArtifactReq.GetFirstVersion().SetContent(models.NewVersionContent())
	createArtifactReq.GetFirstVersion().GetContent().SetContent(&contentStr)
	createArtifactReq.GetFirstVersion().GetContent().SetContentType(&contentType)

	createArtifactResp, err := client.Groups().ByGroupId("default").Artifacts().Post(context.Background(), createArtifactReq, nil)
	assert.Nil(t, err)

	resultArtifact, err := client.Groups().ByGroupId("default").Artifacts().ByArtifactId(*createArtifactResp.GetArtifact().GetArtifactId()).Versions().ByVersionExpression("branch=latest").Content().Get(context.Background(), nil)
	assert.Nil(t, err)

	assert.Equal(t, contentStr, string(resultArtifact[:]))
}
