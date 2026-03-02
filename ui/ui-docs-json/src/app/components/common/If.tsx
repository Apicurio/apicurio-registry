import React, { FunctionComponent } from "react";

/**
 * Properties
 */
export type IfProps = {
    condition: boolean | (() => boolean);
    children?: React.ReactNode;
};

/**
 * Wrapper around a set of arbitrary child elements and displays them only if the
 * indicated condition is true.
 */
export const If: FunctionComponent<IfProps> = ({ condition, children }: IfProps) => {
    const accept = () => {
        if (typeof condition === "boolean") {
            return condition;
        } else {
            return condition();
        }
    };

    return (accept() ? <React.Fragment children={children} /> : <React.Fragment />);
};
