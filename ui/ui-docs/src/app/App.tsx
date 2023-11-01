import "./App.css";
import "@patternfly/patternfly/patternfly.css";
import "@patternfly/patternfly/patternfly-addons.css";

import { FunctionComponent } from "react";


export type AppProps = {
    // No props
};


/**
 * The main application class.
 */
export const App: FunctionComponent<AppProps> = () => {
    return (
        <h1>To Be Done!</h1>
    );
};
