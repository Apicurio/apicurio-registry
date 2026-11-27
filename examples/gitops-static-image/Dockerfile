FROM alpine/git:latest AS builder
WORKDIR /repo
COPY content/ .
RUN git init -b main \
    && git config user.name "build" \
    && git config user.email "build@example.com" \
    && git add -A \
    && git commit -m "Bake static registry content"

FROM busybox:stable
COPY --from=builder /repo /repo
