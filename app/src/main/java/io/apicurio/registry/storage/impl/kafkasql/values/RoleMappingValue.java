package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.ToString;


@RegisterForReflection
@ToString
public class RoleMappingValue extends AbstractMessageValue {

    private String role;
    private String principalName;

    /**
     * Creator method.
     * @param action
     * @param role
     * @param principalName
     */
    public static final RoleMappingValue create(ActionType action, String role, String principalName) {
        RoleMappingValue value = new RoleMappingValue();
        value.setAction(action);
        value.setRole(role);
        value.setPrincipalName(principalName);
        return value;
    }

    /**
     * @see MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.RoleMapping;
    }

    /**
     * @return the role
     */
    public String getRole() {
        return role;
    }

    /**
     * @param role the role to set
     */
    public void setRole(String role) {
        this.role = role;
    }

    /**
     * @return the principalName
     */
    public String getPrincipalName() {
        return principalName;
    }

    /**
     * @param principalName the principalName to set
     */
    public void setPrincipalName(String principalName) {
        this.principalName = principalName;
    }

}
