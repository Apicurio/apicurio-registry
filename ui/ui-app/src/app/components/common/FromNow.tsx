import { FunctionComponent, useEffect, useState } from "react";
import { DateTime } from "luxon";


export type FromNowProps = {
    date: string | undefined;
};


export const FromNow: FunctionComponent<FromNowProps> = (props: FromNowProps) => {
    const [formattedDate, setFormattedDate] = useState<string | null>(null);

    useEffect(() => {
        if (props.date) {
            const luxonDT: DateTime = DateTime.fromISO(props.date);
            setFormattedDate(luxonDT.toRelative());
        } else {
            setFormattedDate(null);
        }
    }, [props.date]);

    return (
        <span>{ formattedDate || "" }</span>
    );
};
