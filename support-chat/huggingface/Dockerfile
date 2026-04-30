FROM quay.io/apicurio/apicurio-registry:latest AS registry
FROM quay.io/apicurio/apicurio-registry-support-chat:latest-snapshot AS support-chat

FROM eclipse-temurin:21-jre

RUN apt-get update && apt-get install -y --no-install-recommends supervisor && \
    rm -rf /var/lib/apt/lists/*

RUN useradd -m -u 1001 appuser

COPY --from=registry /deployments/quarkus-app /opt/registry/
COPY --from=support-chat /deployments/ /opt/support-chat/

COPY supervisord.conf /etc/supervisor/conf.d/supervisord.conf

RUN mkdir -p /var/log/supervisor && chown -R appuser:appuser /var/log/supervisor

EXPOSE 7860

USER appuser

CMD ["supervisord", "-c", "/etc/supervisor/conf.d/supervisord.conf"]
