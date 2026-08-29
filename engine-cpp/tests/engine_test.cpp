// Unit tests for the matching engine core. Kept dependency free so CI needs
// nothing beyond a C++20 compiler and CTest.
#include <cassert>
#include <iostream>
#include <string>

#include "velox/engine_service.hpp"
#include "velox/order_book.hpp"

namespace {

void full_fill_at_maker_price() {
  velox::OrderBook book;
  const auto maker = book.submit(velox::Side::Sell, 10100, 50);
  assert(maker.resting_quantity == 50);

  const auto taker = book.submit(velox::Side::Buy, 10150, 50);
  assert(taker.filled_quantity == 50);
  assert(taker.resting_quantity == 0);
  assert(taker.fills.size() == 1);
  assert(taker.fills.front().price_ticks == 10100);  // maker price wins
  assert(book.last_price_ticks() == 10100);
  assert(book.resting_orders() == 0);
}

void partial_fill_leaves_remainder_resting() {
  velox::OrderBook book;
  book.submit(velox::Side::Sell, 10000, 30);
  const auto taker = book.submit(velox::Side::Buy, 10000, 100);
  assert(taker.filled_quantity == 30);
  assert(taker.resting_quantity == 70);
  assert(book.best_bid_ticks().value() == 10000);
  assert(!book.best_ask_ticks().has_value());
}

void time_priority_is_respected() {
  velox::OrderBook book;
  const auto first = book.submit(velox::Side::Buy, 9900, 10);
  book.submit(velox::Side::Buy, 9900, 10);
  const auto taker = book.submit(velox::Side::Sell, 9900, 10);
  assert(taker.fills.size() == 1);
  assert(taker.fills.front().maker_order_id == first.order_id);
}

void no_cross_when_prices_do_not_meet() {
  velox::OrderBook book;
  book.submit(velox::Side::Sell, 10500, 25);
  const auto taker = book.submit(velox::Side::Buy, 10400, 25);
  assert(taker.filled_quantity == 0);
  assert(taker.resting_quantity == 25);
  assert(book.depth(5).bids.size() == 1);
  assert(book.depth(5).asks.size() == 1);
}

void cancel_removes_resting_liquidity() {
  velox::OrderBook book;
  const auto resting = book.submit(velox::Side::Buy, 9800, 40);
  assert(book.cancel(resting.order_id));
  assert(!book.cancel(resting.order_id));
  assert(book.resting_orders() == 0);
  assert(book.depth(5).bids.empty());
}

void invalid_orders_are_rejected() {
  velox::OrderBook book;
  assert(book.submit(velox::Side::Buy, 10000, 0).order_id == 0);
  assert(book.submit(velox::Side::Buy, -5, 10).order_id == 0);
  assert(book.resting_orders() == 0);
}

void service_rejects_malformed_payloads() {
  velox::EngineService engine;
  std::string error;
  assert(engine.submit_json(R"({"side":"HOLD","quantity":1,"limitPrice":10})", error).empty());
  assert(!error.empty());

  error.clear();
  assert(engine.submit_json(R"({"side":"BUY","quantity":-4,"limitPrice":10})", error).empty());
  assert(!error.empty());

  error.clear();
  const std::string accepted =
      engine.submit_json(R"({"side":"BUY","quantity":5,"limitPrice":187.42})", error);
  assert(error.empty());
  assert(accepted.find("\"orderId\"") != std::string::npos);
  assert(engine.quote_json().find("\"symbol\":\"VLX\"") != std::string::npos);
  assert(engine.depth_json(5).find("\"bids\"") != std::string::npos);
}

}  // namespace

int main() {
  full_fill_at_maker_price();
  partial_fill_leaves_remainder_resting();
  time_priority_is_respected();
  no_cross_when_prices_do_not_meet();
  cancel_removes_resting_liquidity();
  invalid_orders_are_rejected();
  service_rejects_malformed_payloads();
  std::cout << "all engine tests passed\n";
  return 0;
}
