#include "velox/engine_service.hpp"

#include <algorithm>
#include <cctype>
#include <chrono>
#include <cstdlib>
#include <exception>
#include <random>
#include <sstream>

namespace velox {
namespace {

std::mt19937_64& rng() {
  static thread_local std::mt19937_64 engine{std::random_device{}()};
  return engine;
}

std::string price_to_string(std::int64_t ticks) {
  std::ostringstream out;
  const std::int64_t cents = ticks % 100;
  out << ticks / 100 << '.' << (cents < 10 ? "0" : "") << cents;
  return out.str();
}

std::int64_t epoch_millis() {
  using namespace std::chrono;
  return duration_cast<milliseconds>(system_clock::now().time_since_epoch()).count();
}

// Extracts one field from a flat JSON object. The engine only ever receives small,
// well-known payloads from the platform, so a full parser would be overkill.
std::string read_field(const std::string& body, const std::string& key) {
  const std::string needle = "\"" + key + "\"";
  const auto key_pos = body.find(needle);
  if (key_pos == std::string::npos) return {};
  auto cursor = body.find(':', key_pos + needle.size());
  if (cursor == std::string::npos) return {};
  ++cursor;
  while (cursor < body.size() && std::isspace(static_cast<unsigned char>(body[cursor]))) ++cursor;
  if (cursor >= body.size()) return {};
  if (body[cursor] == '"') {
    const auto end = body.find('"', ++cursor);
    if (end == std::string::npos) return {};
    return body.substr(cursor, end - cursor);
  }
  const auto end = body.find_first_of(",}", cursor);
  auto value = body.substr(cursor, end == std::string::npos ? std::string::npos : end - cursor);
  while (!value.empty() && std::isspace(static_cast<unsigned char>(value.back()))) value.pop_back();
  return value;
}

bool parse_quantity(const std::string& text, std::int64_t& out) {
  if (text.empty() || text.size() > 18) return false;
  try {
    std::size_t consumed = 0;
    const long long parsed = std::stoll(text, &consumed);
    if (consumed != text.size()) return false;
    out = static_cast<std::int64_t>(parsed);
    return true;
  } catch (const std::exception&) {
    return false;
  }
}

bool parse_price_ticks(const std::string& text, std::int64_t& ticks) {
  if (text.empty() || text.size() > 18) return false;
  try {
    std::size_t consumed = 0;
    const double parsed = std::stod(text, &consumed);
    if (consumed != text.size() || !(parsed > 0.0) || parsed > 1'000'000.0) return false;
    ticks = static_cast<std::int64_t>(parsed * 100.0 + 0.5);
    return ticks > 0;
  } catch (const std::exception&) {
    return false;
  }
}

}  // namespace

EngineService::EngineService() {
  const std::lock_guard<std::mutex> guard(mutex_);
  seed_liquidity_locked();
}

void EngineService::seed_liquidity_locked() {
  const std::int64_t mid = book_.last_price_ticks();
  std::uniform_int_distribution<std::int64_t> size(80, 600);
  for (std::int64_t step = 1; step <= 6; ++step) {
    book_.submit(Side::Buy, mid - step * 3, size(rng()));
    book_.submit(Side::Sell, mid + step * 3, size(rng()));
  }
}

void EngineService::drift() {
  const std::lock_guard<std::mutex> guard(mutex_);
  std::normal_distribution<double> shock(0.0, 18.0);
  const std::int64_t mid = std::clamp<std::int64_t>(
      book_.last_price_ticks() + static_cast<std::int64_t>(shock(rng())), 1000, 10'000'000);
  book_.set_last_price_ticks(mid);
  if (++tick_count_ % 5 == 0 || book_.resting_orders() < 6) seed_liquidity_locked();
}

std::string EngineService::quote_json() {
  const std::lock_guard<std::mutex> guard(mutex_);
  const std::int64_t last = book_.last_price_ticks();
  const std::int64_t bid = book_.best_bid_ticks().value_or(last - 3);
  const std::int64_t ask = book_.best_ask_ticks().value_or(last + 3);
  std::ostringstream out;
  out << R"({"symbol":"VLX","price":)" << price_to_string(last)
      << R"(,"bid":)" << price_to_string(bid)
      << R"(,"ask":)" << price_to_string(ask)
      << R"(,"restingOrders":)" << book_.resting_orders()
      << R"(,"timestamp":)" << epoch_millis() << '}';
  return out.str();
}

std::string EngineService::depth_json(std::size_t levels) {
  const std::lock_guard<std::mutex> guard(mutex_);
  const Depth snapshot = book_.depth(levels);
  const auto render = [](const std::vector<Level>& side, std::ostringstream& out) {
    out << '[';
    for (std::size_t index = 0; index < side.size(); ++index) {
      if (index > 0) out << ',';
      out << R"({"price":)" << price_to_string(side[index].price_ticks)
          << R"(,"quantity":)" << side[index].quantity << '}';
    }
    out << ']';
  };
  std::ostringstream out;
  out << R"({"symbol":"VLX","bids":)";
  render(snapshot.bids, out);
  out << R"(,"asks":)";
  render(snapshot.asks, out);
  out << R"(,"timestamp":)" << epoch_millis() << '}';
  return out.str();
}

std::string EngineService::submit_json(const std::string& request_body, std::string& error) {
  const std::string side_text = read_field(request_body, "side");
  std::int64_t quantity = 0;
  std::int64_t price_ticks = 0;
  if (side_text != "BUY" && side_text != "SELL") {
    error = "side must be BUY or SELL";
    return {};
  }
  if (!parse_quantity(read_field(request_body, "quantity"), quantity) || quantity <= 0 ||
      quantity > 1'000'000) {
    error = "quantity must be a positive integer up to 1000000";
    return {};
  }
  if (!parse_price_ticks(read_field(request_body, "limitPrice"), price_ticks)) {
    error = "limitPrice must be a positive decimal";
    return {};
  }

  const auto started = std::chrono::steady_clock::now();
  MatchResult result;
  {
    const std::lock_guard<std::mutex> guard(mutex_);
    result = book_.submit(side_text == "BUY" ? Side::Buy : Side::Sell, price_ticks, quantity);
  }
  const auto latency_nanos = std::chrono::duration_cast<std::chrono::nanoseconds>(
                                 std::chrono::steady_clock::now() - started)
                                 .count();

  const char* status = result.resting_quantity == 0
                           ? "FILLED"
                           : (result.filled_quantity > 0 ? "PARTIALLY_FILLED" : "RESTING");
  std::ostringstream out;
  out << R"({"orderId":)" << result.order_id
      << R"(,"status":")" << status
      << R"(","filledQuantity":)" << result.filled_quantity
      << R"(,"restingQuantity":)" << result.resting_quantity
      << R"(,"matchLatencyNanos":)" << latency_nanos
      << R"(,"fills":[)";
  for (std::size_t index = 0; index < result.fills.size(); ++index) {
    if (index > 0) out << ',';
    out << R"({"price":)" << price_to_string(result.fills[index].price_ticks)
        << R"(,"quantity":)" << result.fills[index].quantity << '}';
  }
  out << R"(],"executedAt":)" << epoch_millis() << '}';
  return out.str();
}

}  // namespace velox
