import { generate } from "./dist/main.js";

try {
    if (window.kiota === undefined) {
        window.kiota = {};
        window.kiota.generate = generate;
        console.log("Kiota is now available in the window");
    }
} catch (e) {
    console.warn("Kiota not available");
}
