# Build stage: compile and run the engine unit tests before shipping.
FROM gcc:14 AS build
RUN apt-get update && apt-get install -y --no-install-recommends cmake \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /src
COPY CMakeLists.txt ./
COPY include include
COPY src src
COPY tests tests
RUN cmake -S . -B build -DCMAKE_BUILD_TYPE=Release \
    && cmake --build build --parallel \
    && ctest --test-dir build --output-on-failure

# Runtime stage: minimal image running as an unprivileged user.
FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --uid 10001 velox
COPY --from=build /src/build/veloxtrade-engine /usr/local/bin/veloxtrade-engine
USER velox
ENV ENGINE_PORT=8081
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=3s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8081/health || exit 1
CMD ["veloxtrade-engine"]
