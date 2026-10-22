import { Label } from "@patternfly/react-core";
import {
  CheckCircleIcon,
  ExclamationCircleIcon,
  QuestionCircleIcon,
} from "@patternfly/react-icons";
import { useTranslation } from "react-i18next";
import { Condition } from "../utils/k8s";

interface RegistryStatusBadgeProps {
  condition?: Condition;
}

const RegistryStatusBadge: React.FC<RegistryStatusBadgeProps> = ({
  condition,
}) => {
  const { t } = useTranslation("plugin__apicurio-registry");

  if (!condition) {
    return (
      <Label color="grey" icon={<QuestionCircleIcon />}>
        {t("Unknown")}
      </Label>
    );
  }

  switch (condition.status) {
    case "True":
      return (
        <Label color="green" icon={<CheckCircleIcon />}>
          {t("Ready")}
        </Label>
      );
    case "False":
      return (
        <Label color="red" icon={<ExclamationCircleIcon />}>
          {t("Not Ready")}
        </Label>
      );
    default:
      return (
        <Label color="grey" icon={<QuestionCircleIcon />}>
          {t("Unknown")}
        </Label>
      );
  }
};

export default RegistryStatusBadge;
