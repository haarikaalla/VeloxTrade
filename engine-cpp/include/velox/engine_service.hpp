#pragma once

#include <cstddef>
#include <mutex>
#include <string>

#include "velox/order_book.hpp"

namespace velox {

// Thread-safe facade over the order book that renders JSON for the Java platform.
// Serialisation lives here so the engine keeps a zero third-party dependency footprint.
class EngineService {
 public:
  EngineService();

  std::string quote_json();
  std::string depth_json(std::size_t levels);
  std::string submit_json(const std::string& request_body, std::string& error);
  void drift();  // advances the simulated mid price and refreshes resting liquidity

 private:
  void seed_liquidity_locked();

  std::mutex mutex_;
  OrderBook book_;
  std::uint64_t tick_count_{0};
};

}  // namespace velox
