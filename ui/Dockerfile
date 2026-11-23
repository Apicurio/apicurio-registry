# Build a minimal root file system on top of ubi-micro, which ships no package manager. Only
# nginx and node.js (used by the start-up configuration scripts) are installed, instead of the
# full ubi10/nginx-126 image.
FROM registry.access.redhat.com/ubi10/ubi-micro@sha256:2ad0f98cb13160c4773c945ef93f88b25831c7f27cd3780d72a7e78b210ea72b AS micro

FROM registry.access.redhat.com/ubi10/ubi@sha256:87ceab8634a53a36c75a8ba7282f09f11c46ab3e8906db6c6d2b3cd2a7dc4f4e AS builder
COPY --from=micro / /mnt/rootfs
RUN dnf install -y --installroot /mnt/rootfs --releasever 10 \
        --setopt install_weak_deps=false --setopt tsflags=nodocs --nodocs \
        nginx nodejs \
    && dnf clean all --installroot /mnt/rootfs \
    && rm -rf /mnt/rootfs/var/cache/* /mnt/rootfs/var/log/dnf* /mnt/rootfs/var/log/yum*

# Generate the self-signed certificate used by the https listener
RUN dnf install -y openssl && \
    mkdir -p /mnt/rootfs/etc/pki/nginx/private && \
    openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
        -keyout /mnt/rootfs/etc/pki/nginx/private/server.key \
        -out /mnt/rootfs/etc/pki/nginx/server.crt --batch && \
    chmod 755 /mnt/rootfs/etc/pki/nginx/private/server.key /mnt/rootfs/etc/pki/nginx/server.crt

# Copy configuration scripts and nginx config, converting them from DOS line endings
# (carriage return + line feed) to Unix line endings to avoid failures on Windows checkouts.
COPY .docker-scripts/create-config.cjs .docker-scripts/update-base-href.cjs .docker-scripts/entrypoint.sh /mnt/rootfs/usr/local/bin/
COPY .docker-scripts/nginx.conf /mnt/rootfs/etc/nginx/nginx.conf
RUN sed -i 's/\r$//' /mnt/rootfs/usr/local/bin/create-config.cjs \
        /mnt/rootfs/usr/local/bin/update-base-href.cjs \
        /mnt/rootfs/usr/local/bin/entrypoint.sh \
        /mnt/rootfs/etc/nginx/nginx.conf \
    && chmod 775 /mnt/rootfs/usr/local/bin/*

# Directories that nginx and the start-up scripts write to must be writable by the
# (arbitrary, OpenShift-assigned) container user, which is always in group 0.
RUN mkdir -p /mnt/rootfs/opt/app-root/src /mnt/rootfs/opt/app-root/etc/nginx.d \
        /mnt/rootfs/opt/app-root/etc/nginx.default.d /mnt/rootfs/var/lib/nginx/tmp \
    && ln -sf /dev/stdout /mnt/rootfs/var/log/nginx/access.log \
    && ln -sf /dev/stderr /mnt/rootfs/var/log/nginx/error.log \
    && chown -R 1001:0 /mnt/rootfs/opt/app-root /mnt/rootfs/var/lib/nginx /mnt/rootfs/var/log/nginx \
        /mnt/rootfs/etc/pki/nginx /mnt/rootfs/usr/local/bin /mnt/rootfs/etc/nginx/nginx.conf \
    && chmod -R g=u /mnt/rootfs/opt/app-root /mnt/rootfs/var/lib/nginx /mnt/rootfs/var/log/nginx \
        /mnt/rootfs/usr/local/bin

FROM scratch
COPY --from=builder /mnt/rootfs/ /

ENV LANG="C.utf8" \
    HOME="/opt/app-root/src"

WORKDIR /opt/app-root/src

# Copy dist files
COPY --chown=1001:0 dist/ .

RUN chmod -R g+w /opt/app-root/src

# Expose port 8080 for http and 1337 for https
EXPOSE 8080 1337

USER 1001
CMD ["/usr/local/bin/entrypoint.sh"]
