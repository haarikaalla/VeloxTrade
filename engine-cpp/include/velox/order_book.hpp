// Price-time priority limit order book for the VeloxTrade simulated market.
#pragma once

#include <cstdint>
#include <deque>
#include <functional>
#include <map>
#include <optional>
#include <vector>

namespace velox {

enum class Side { Buy, Sell };

struct Order {
  std::uint64_t id{};
  Side side{Side::Buy};
  std::int64_t price_ticks{};  // price in cents; integer maths keeps matching exact
  std::int64_t quantity{};
  std::uint64_t sequence{};
};

struct Fill {
  std::uint64_t taker_order_id{};
  std::uint64_t maker_order_id{};
  std::int64_t price_ticks{};
  std::int64_t quantity{};
};

struct Level {
  std::int64_t price_ticks{};
  std::int64_t quantity{};
};

struct Depth {
  std::vector<Level> bids;
  std::vector<Level> asks;
};

struct MatchResult {
  std::uint64_t order_id{};
  std::int64_t filled_quantity{};
  std::int64_t resting_quantity{};
  std::vector<Fill> fills;
};

// Not thread safe on its own; callers serialise access (see EngineService).
class OrderBook {
 public:
  MatchResult submit(Side side, std::int64_t price_ticks, std::int64_t quantity);
  bool cancel(std::uint64_t order_id);
  [[nodiscard]] Depth depth(std::size_t levels) const;
  [[nodiscard]] std::int64_t last_price_ticks() const noexcept { return last_price_ticks_; }
  [[nodiscard]] std::optional<std::int64_t> best_bid_ticks() const;
  [[nodiscard]] std::optional<std::int64_t> best_ask_ticks() const;
  [[nodiscard]] std::size_t resting_orders() const noexcept { return resting_count_; }
  void set_last_price_ticks(std::int64_t price_ticks) noexcept { last_price_ticks_ = price_ticks; }

 private:
  using BidLadder = std::map<std::int64_t, std::deque<Order>, std::greater<std::int64_t>>;
  using AskLadder = std::map<std::int64_t, std::deque<Order>, std::less<std::int64_t>>;

  template <typename Ladder, typename Crosses>
  void match(Ladder& ladder, Order& taker, Crosses crosses, MatchResult& result);

  BidLadder bids_;
  AskLadder asks_;
  std::uint64_t next_order_id_{1};
  std::uint64_t next_sequence_{1};
  std::size_t resting_count_{0};
  std::int64_t last_price_ticks_{18742};
};

}  // namespace velox
