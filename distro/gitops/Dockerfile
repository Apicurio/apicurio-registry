FROM registry.access.redhat.com/ubi10/ubi-minimal@sha256:3948fdfe71007909b37faf48c52eda28bfab7c4e440d6f4d4619422d06ceeb4c

RUN microdnf install -y git-core openssh-clients openssh-server shadow-utils jq && \
    microdnf clean all

# Create a dedicated 'git' user for SSH push operations.
# The user's shell is git-shell to restrict access to git commands only.
#
# OpenShift compatibility:
#
# OCP runs containers with arbitrary UIDs (e.g., 1000830000) in group 0 (root).
# The Dockerfile's USER directive is ignored — the actual UID is assigned by OCP.
# To support this:
#
#   1. Directories are made group-writable (chgrp 0, chmod g=u) so the arbitrary
#      UID can create files via group permissions.
#   2. /etc/passwd is made writable so the entrypoint can add a passwd entry
#      mapping the arbitrary UID to the 'git' username (required for SSH/git-shell).
#   3. The entrypoint replaces the original git:x:1000 entry (not appends) because
#      sshd looks up the user by name and tries setuid to the first match.
#   4. sshd runs with StrictModes=no because StrictModes rejects group-writable
#      home directories, which are required for the arbitrary UID to write runtime
#      files (SSH host keys, config). See sshd_config.template for details.
#   5. Runtime chmod calls in the entrypoint tolerate failures (2>/dev/null || true)
#      because the arbitrary UID cannot change permission bits on files owned by
#      UID 1000 — it can only read/write via group permissions.
RUN useradd -m -d /home/git -s /usr/bin/git-shell git && \
    mkdir -p /home/git/.ssh /home/git/.sshd /repos && \
    chown -R git:git /home/git /repos && \
    chmod 700 /home/git/.ssh && \
    chgrp -R 0 /home/git /repos && \
    chmod -R g=u /home/git /repos && \
    chmod g=u /etc/passwd

COPY entrypoint.sh /usr/local/bin/entrypoint.sh
COPY ssh_config.template sshd_config.template /usr/local/share/apicurio-gitops/
RUN chmod 755 /usr/local/bin/entrypoint.sh

USER git

VOLUME ["/repos"]

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
