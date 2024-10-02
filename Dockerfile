FROM ubuntu:24.10

RUN apt-get update && apt-get install -y openjdk-17-jdk
RUN apt-get install -y iproute2

COPY ./build/install/vertx-bittorrent/ /app/vertx-bittorrent/

WORKDIR /app

# ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk

# CMD sysctl -w kernel.core_pattern=/coredump/core-%e.%p.%h.%t && tc qdisc add dev eth0 root tbf rate 128kbps burst 10kb latency 50ms && /app/vertx-bittorrent/bin/vertx-bittorrent --id 1 --torrent-dir /torrents --data-dir /data ${EXTRA_OPTS}
CMD tc qdisc add dev eth0 root tbf rate 128kbps burst 10kb latency 50ms && /app/vertx-bittorrent/bin/vertx-bittorrent --id ${NODE_ID} --torrent-dir ./torrents --data-dir ./data ${EXTRA_OPTS}


# FROM amazoncorretto:17-alpine3.20-full
#
# RUN apk add iproute2-tc libstdc++ blas gcc gdb openblas openblas-dev musl
# RUN wget -q -O /etc/apk/keys/sgerrand.rsa.pub https://alpine-pkgs.sgerrand.com/sgerrand.rsa.pub && \
#     wget https://github.com/sgerrand/alpine-pkg-glibc/releases/download/2.35-r1/glibc-2.35-r1.apk && \
#     apk add glibc-2.35-r1.apk
#
# COPY ./build/install/vertx-bittorrent/ /app/vertx-bittorrent/
#
# WORKDIR /app
#
# # ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk
#
# CMD sysctl -w kernel.core_pattern=/coredump/core-%e.%p.%h.%t && tc qdisc add dev eth0 root tbf rate 128kbps burst 10kb latency 50ms && /app/vertx-bittorrent/bin/vertx-bittorrent --id 1 --torrent-dir /torrents --data-dir /data ${EXTRA_OPTS}

