FROM alpine:latest

WORKDIR /data

# Installa git
RUN apk add --no-cache git

# Clona il repository dei dati Orekit
RUN git clone --depth 1 https://gitlab.orekit.org/orekit/orekit-data.git /tmp/orekit && \
    mv /tmp/orekit/* . && \
    rm -rf /tmp/orekit/.git && \
    echo "✅ Orekit data ready" && \
    ls -la

# Il container mantiene i dati disponibili
CMD ["tail", "-f", "/dev/null"]
