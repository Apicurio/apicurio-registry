export class StringUtils {

    static isJSON(value: string): boolean {
        return value !== null && value !== undefined && typeof value === "string" &&
            (StringUtils.startsWith(value, "{") || StringUtils.startsWith(value, "["));
    }

    static isXml(value: string): boolean {
        return value !== null && value !== undefined && typeof value === "string" &&
            StringUtils.startsWith(value, "<");
    }

    static startsWith(value: string, swith: string): boolean {
        return value !== null && value !== undefined && value.indexOf(swith) === 0;
    }
}
