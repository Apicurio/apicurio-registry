package io.apicurio.registry.operator.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.apicur.registry.v1.ApicurioRegistry;
import io.apicurio.registry.operator.Constants;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesList;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.rbac.ClusterRoleBinding;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.quarkiverse.operatorsdk.runtime.QuarkusConfigurationService;
import io.quarkus.logging.Log;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.enterprise.util.TypeLiteral;
import java.io.FileInputStream;
import java.time.Duration;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

public class ITBase {

  public static final String DEPLOYMENT_TARGET = "test.operator.deployment-target";
  public static final String OPERATOR_DEPLOYMENT_PROP = "test.operator.deployment";
  public static final String CLEANUP = "test.operator.cleanup";
  public static final String GENERATED_RESOURCES_FOLDER = "target/kubernetes/";
  public static final String CRD_FILE = "../model/src/main/resources/kubernetes/crd.yml";

  public enum OperatorDeployment {
    local,
    remote
  }

  protected static OperatorDeployment operatorDeployment;
  protected static Instance<Reconciler<? extends HasMetadata>> reconcilers;
  protected static QuarkusConfigurationService configuration;
  protected static KubernetesClient client;
  protected static String deploymentTarget;
  protected static String namespace;
  protected static boolean cleanup;
  private static Operator operator;

  @BeforeAll
  public static void before() throws Exception {
    configuration = CDI.current().select(QuarkusConfigurationService.class).get();
    reconcilers = CDI.current().select(new TypeLiteral<>() {});
    operatorDeployment =
        ConfigProvider.getConfig()
            .getOptionalValue(OPERATOR_DEPLOYMENT_PROP, OperatorDeployment.class)
            .orElse(OperatorDeployment.local);
    deploymentTarget =
        ConfigProvider.getConfig()
            .getOptionalValue(DEPLOYMENT_TARGET, String.class)
            .orElse("kubernetes");
    cleanup = ConfigProvider.getConfig().getOptionalValue(CLEANUP, Boolean.class).orElse(true);

    setDefaultAwaitilityTimings();
    calculateNamespace();
    createK8sClient();
    createCRDs();
    createNamespace();

    if (operatorDeployment == OperatorDeployment.remote) {
      createGeneratedResources();
    } else {
      createOperator();
      registerReconcilers();
      operator.start();
    }
  }

  @BeforeEach
  public void beforeEach(TestInfo testInfo) {
    String testClassName = testInfo.getTestClass().map(c -> c.getSimpleName() + ".").orElse("");
    Log.info(
        "\n------- STARTING: "
            + testClassName
            + testInfo.getDisplayName()
            + "\n"
            + "------- Namespace: "
            + namespace
            + "\n"
            + "------- Mode: "
            + ((operatorDeployment == OperatorDeployment.remote) ? "remote" : "local")
            + "\n"
            + "------- Deployment target: "
            + deploymentTarget);
  }

  private static void createK8sClient() {
    client =
        new KubernetesClientBuilder()
            .withConfig(
                new ConfigBuilder(Config.autoConfigure(null)).withNamespace(namespace).build())
            .build();
  }

  private static void createGeneratedResources() throws Exception {
    Log.info("Creating generated resources into Namespace " + namespace);
    try (var fis = new FileInputStream(GENERATED_RESOURCES_FOLDER + deploymentTarget + ".json")) {
      KubernetesList resources = Serialization.unmarshal(fis);

      resources.getItems().stream()
          .forEach(
              r -> {
                if (r.getKind().equals("ClusterRoleBinding") && r instanceof ClusterRoleBinding) {
                  var crb = (ClusterRoleBinding) r;
                  crb.getSubjects().stream().forEach(s -> s.setNamespace(getNamespace()));
                }
                client.resource(r).inNamespace(getNamespace()).createOrReplace();
              });
    }
  }

  private static void cleanGeneratedResources() throws Exception {
    if (cleanup) {
      Log.info("Deleting generated resources from Namespace " + namespace);
      try (var fis = new FileInputStream(GENERATED_RESOURCES_FOLDER + deploymentTarget + ".json")) {
        KubernetesList resources = Serialization.unmarshal(fis);

        resources.getItems().stream()
            .forEach(
                r -> {
                  if (r.getKind().equals("ClusterRoleBinding") && r instanceof ClusterRoleBinding) {
                    var crb = (ClusterRoleBinding) r;
                    crb.getSubjects().stream().forEach(s -> s.setNamespace(getNamespace()));
                  }
                  client.resource(r).inNamespace(getNamespace()).delete();
                });
      }
    }
  }

  private static void createCRDs() {
    Log.info("Creating CRDs");
    try {
      var crd = client.load(new FileInputStream(CRD_FILE));
      crd.createOrReplace();
    } catch (Exception e) {
      Log.warn("Failed to create the CRD, retrying", e);
      createCRDs();
    }
  }

  private static void registerReconcilers() {
    Log.info(
        "Registering reconcilers for operator : " + operator + " [" + operatorDeployment + "]");

    for (Reconciler<?> reconciler : reconcilers) {
      Log.info("Register and apply : " + reconciler.getClass().getName());
      operator.register(reconciler);
    }
  }

  private static void createOperator() {
    operator =
        new Operator(
            configurationServiceOverrider -> {
              configurationServiceOverrider.withKubernetesClient(client);
            });
  }

  private static void createNamespace() {
    Log.info("Creating Namespace " + namespace);
    client
        .resource(
            new NamespaceBuilder()
                .withNewMetadata()
                .addToLabels("app", "apicurio-registry-operator-test")
                .withName(namespace)
                .endMetadata()
                .build())
        .create();
  }

  private static void calculateNamespace() {
    namespace = ("apicurio-registry-operator-test-" + UUID.randomUUID()).substring(0, 63);
  }

  private static void setDefaultAwaitilityTimings() {
    Awaitility.setDefaultPollInterval(Duration.ofSeconds(1));
    Awaitility.setDefaultTimeout(Duration.ofSeconds(360));
  }

  @AfterEach
  public void cleanup() {
    if (cleanup) {
      Log.info("Deleting CRs");
      client.resources(ApicurioRegistry.class).delete();
      Awaitility.await()
          .untilAsserted(
              () -> {
                var registryDeployments =
                    client
                        .apps()
                        .deployments()
                        .inNamespace(namespace)
                        .withLabels(Constants.BASIC_LABELS)
                        .list()
                        .getItems();
                assertThat(registryDeployments.size()).isZero();
              });
    }
  }

  @AfterAll
  public static void after() throws Exception {

    if (operatorDeployment == OperatorDeployment.local) {
      Log.info("Stopping Operator");
      operator.stop();

      Log.info("Creating new K8s Client");
      // create a new client bc operator has closed the old one
      createK8sClient();
    } else {
      cleanGeneratedResources();
    }

    if (cleanup) {
      Log.info("Deleting namespace : " + namespace);
      assertThat(client.namespaces().withName(namespace).delete()).isNotNull();
    }
    client.close();
  }

  public static String getNamespace() {
    return namespace;
  }
}
