FROM eclipse-temurin:21-jre

RUN useradd --system --uid 10001 --user-group rendezvous

COPY build/install/rendezvous-service /opt/rendezvous-service

USER rendezvous

EXPOSE 7789/tcp 7790/udp 7800-7899/udp

ENTRYPOINT ["/opt/rendezvous-service/bin/rendezvous-service"]
