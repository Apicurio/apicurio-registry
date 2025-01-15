package io.apicurio.registry.operator.api.v1;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.JsonDeserializer.None;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.apicurio.registry.operator.api.v1.spec.AppSpec;
import io.apicurio.registry.operator.api.v1.spec.StudioUiSpec;
import io.apicurio.registry.operator.api.v1.spec.UiSpec;
import lombok.*;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.annotation.Nulls.SKIP;
import static lombok.AccessLevel.PRIVATE;

@JsonInclude(NON_NULL)
@JsonPropertyOrder({ "app", "ui", "studioUi" })
@JsonDeserialize(using = None.class)
@NoArgsConstructor
@AllArgsConstructor(access = PRIVATE)
@Builder
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class ApicurioRegistry3Spec {

    /**
     * Configure Apicurio Registry backend (app) component.
     */
    @JsonProperty("app")
    @JsonPropertyDescription("""
            Configure Apicurio Registry backend (app) component.""")
    @JsonSetter(nulls = SKIP)
    private AppSpec app;

    /**
     * Configure Apicurio Registry UI component.
     */
    @JsonProperty("ui")
    @JsonPropertyDescription("""
            Configure Apicurio Registry UI component.
            """)
    @JsonSetter(nulls = SKIP)
    private UiSpec ui;

    /**
     * Configure Apicurio Studio UI component.
     */
    @JsonProperty("studioUi")
    @JsonPropertyDescription("""
            Configure Apicurio Studio UI component.""")
    @JsonSetter(nulls = SKIP)
    private StudioUiSpec studioUi;

    public AppSpec withApp() {
        if (app == null) {
            app = new AppSpec();
        }
        return app;
    }

    public UiSpec withUi() {
        if (ui == null) {
            ui = new UiSpec();
        }
        return ui;
    }

    public StudioUiSpec withStudioUi() {
        if (studioUi == null) {
            studioUi = new StudioUiSpec();
        }
        return studioUi;
    }
}
