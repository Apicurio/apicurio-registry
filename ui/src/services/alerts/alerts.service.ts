import { Alerts, AlertVariant, ConfigService } from "../config";
import { Service } from "../baseService";
import { ConfigurationProperty } from "../../models/configurationProperty.model";
import { LoggerService } from "../logger";


export class AlertsService implements Service {

    private config: ConfigService = null;
    private logger: LoggerService = null;

    public init = () => {
        // no init
    };

    public settingChanged(property: ConfigurationProperty, newValue: any): void {
        const alerts: Alerts | undefined = this.config.featureAlertsService();
        let title: string = `${property.label} updated`;
        if (newValue === "true" || newValue === "false") {
            title = `${property.label} turned ${newValue === "true" ? "on" : "off"}`;
        }
        if (alerts && alerts.addAlert) {
            alerts.addAlert({
                title,
                variant: AlertVariant.success,
                dataTestId: "toast-setting-changed"
            });
        } else {
            this.logger.info("[AlertsService] Alerts (toast) service not available: ", title);
            this.logger.debug("[AlertsService] Alerts config is: ", alerts);
        }
    }
}
