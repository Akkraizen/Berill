FROM postgres:17
RUN apt-get update && apt-get install -y --no-install-recommends restic rclone ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY backup.sh /backup.sh
RUN sed -i 's/\r$//' /backup.sh
ENTRYPOINT ["/bin/sh", "/backup.sh"]
