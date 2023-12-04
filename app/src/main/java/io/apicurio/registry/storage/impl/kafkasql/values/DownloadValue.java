package io.apicurio.registry.storage.impl.kafkasql.values;

import io.apicurio.registry.storage.dto.DownloadContextDto;
import io.apicurio.registry.storage.impl.kafkasql.MessageType;
import io.quarkus.runtime.annotations.RegisterForReflection;


@RegisterForReflection
public class DownloadValue extends AbstractMessageValue {

    private DownloadContextDto downloadContext;

    /**
     * Creator method.
     * @param action
     * @param downloadContext
     */
    public static final DownloadValue create(ActionType action, DownloadContextDto downloadContext) {
        DownloadValue value = new DownloadValue();
        value.setAction(action);
        value.setDownloadContext(downloadContext);
        return value;
    }

    /**
     * @see MessageValue#getType()
     */
    @Override
    public MessageType getType() {
        return MessageType.Download;
    }

    /**
     * @return the downloadContext
     */
    public DownloadContextDto getDownloadContext() {
        return downloadContext;
    }

    /**
     * @param downloadContext the downloadContext to set
     */
    public void setDownloadContext(DownloadContextDto downloadContext) {
        this.downloadContext = downloadContext;
    }

}
