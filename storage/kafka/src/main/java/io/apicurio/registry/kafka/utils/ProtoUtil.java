package io.apicurio.registry.kafka.utils;

import io.apicurio.registry.kafka.proto.Reg;

import java.util.UUID;

/**
 * @author Ales Justin
 */
public class ProtoUtil {

    public static UUID convert(Reg.UUID mpUuid) {
        return new UUID(mpUuid.getMsb(), mpUuid.getLsb());
    }

    public static Reg.UUID convert(UUID uuid) {
        return Reg.UUID
            .newBuilder()
            .setMsb(uuid.getMostSignificantBits())
            .setLsb(uuid.getLeastSignificantBits())
            .build();
    }

}