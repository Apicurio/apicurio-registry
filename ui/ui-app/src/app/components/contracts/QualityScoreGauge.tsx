import { FunctionComponent } from "react";
import { Progress, ProgressMeasureLocation, ProgressVariant } from "@patternfly/react-core";

export type QualityScoreGaugeProps = {
    label: string;
    score: number;
};

export const QualityScoreGauge: FunctionComponent<QualityScoreGaugeProps> = ({ label, score }) => {
    const percentage = Math.round(score * 100);

    let variant: ProgressVariant | undefined;
    if (percentage >= 70) {
        variant = ProgressVariant.success;
    } else if (percentage >= 40) {
        variant = ProgressVariant.warning;
    } else {
        variant = ProgressVariant.danger;
    }

    return (
        <Progress
            title={label}
            value={percentage}
            measureLocation={ProgressMeasureLocation.outside}
            variant={variant}
            aria-label={`${label}: ${percentage}%`}
        />
    );
};
