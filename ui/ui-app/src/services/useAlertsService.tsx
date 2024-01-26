import { ConfigurationProperty } from "@models/configurationProperty.model.ts";


export interface AlertsService {
    settingChanged(property: ConfigurationProperty, newValue: any): void;
}


/**
 * React hook to get the Alerts service.
 */
export const useAlertsService: () => AlertsService = (): AlertsService => {
    return {
        settingChanged(property: ConfigurationProperty, newValue: any): void {
            console.info(property, newValue);
        },
    };
};
