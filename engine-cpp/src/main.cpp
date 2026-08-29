// VeloxTrade matching engine entry point.
//
// Exposes the in-memory limit order book over a small HTTP/JSON API on the
// internal service network and continuously drifts the simulated mid price.
#include <atomic>
#include <chrono>
#include <cstdlib>
#include <string>
#include <thread>

#include "velox/engine_service.hpp"
#include "velox/http_server.hpp"

namespace {

unsigned short port_from_env() {
  const char* raw = std::getenv("ENGINE_PORT");
  if (raw == nullptr) return 8081;
  try {
    const int parsed = std::stoi(raw);
    if (parsed < 1 || parsed > 65535) return 8081;
    return static_cast<unsigned short>(parsed);
  } catch (const std::exception&) {
    return 8081;
  }
}

}  // namespace

int main() {
  velox::EngineService engine;

  std::thread([&engine] {
    while (true) {
      std::this_thread::sleep_for(std::chrono::milliseconds(500));
      engine.drift();
    }
  }).detach();

  velox::HttpServer server("0.0.0.0", port_from_env());
  const bool started = server.serve([&engine](const velox::HttpRequest& request) {
    velox::HttpResponse response;
    if (request.method == "GET" && request.path == "/health") {
      response.body = R"({"status":"UP","service":"veloxtrade-engine"})";
    } else if (request.method == "GET" && request.path == "/quote") {
      response.body = engine.quote_json();
    } else if (request.method == "GET" && request.path.rfind("/depth", 0) == 0) {
      response.body = engine.depth_json(8);
    } else if (request.method == "POST" && request.path == "/orders") {
      std::string error;
      const std::string result = engine.submit_json(request.body, error);
      if (result.empty()) {
        response.status = 400;
        response.body = R"({"error":")" + error + R"("})";
      } else {
        response.body = result;
      }
    } else if (request.path == "/health" || request.path == "/quote" ||
               request.path == "/orders") {
      response.status = 405;
      response.body = R"({"error":"method not allowed"})";
    } else {
      response.status = 404;
      response.body = R"({"error":"unknown route"})";
    }
    return response;
  });

  return started ? 0 : 1;
}
