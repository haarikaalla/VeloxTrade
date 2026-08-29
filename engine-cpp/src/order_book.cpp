#include "velox/order_book.hpp"

#include <algorithm>

namespace velox {
namespace {
constexpr std::int64_t kMaxQuantity = 1'000'000;
constexpr std::int64_t kMaxPriceTicks = 100'000'000;
}  // namespace

template <typename Ladder, typename Crosses>
void OrderBook::match(Ladder& ladder, Order& taker, Crosses crosses, MatchResult& result) {
  while (taker.quantity > 0 && !ladder.empty()) {
    auto best = ladder.begin();
    if (!crosses(best->first, taker.price_ticks)) break;
    auto& queue = best->second;
    while (taker.quantity > 0 && !queue.empty()) {
      Order& maker = queue.front();
      const std::int64_t traded = std::min(taker.quantity, maker.quantity);
      result.fills.push_back({taker.id, maker.id, maker.price_ticks, traded});
      taker.quantity -= traded;
      maker.quantity -= traded;
      result.filled_quantity += traded;
      last_price_ticks_ = maker.price_ticks;
      if (maker.quantity == 0) {
        queue.pop_front();
        --resting_count_;
      }
    }
    if (queue.empty()) ladder.erase(best);
  }
}

MatchResult OrderBook::submit(Side side, std::int64_t price_ticks, std::int64_t quantity) {
  MatchResult result;
  if (quantity <= 0 || quantity > kMaxQuantity) return result;
  if (price_ticks <= 0 || price_ticks > kMaxPriceTicks) return result;

  Order taker{next_order_id_++, side, price_ticks, quantity, next_sequence_++};
  result.order_id = taker.id;

  if (side == Side::Buy) {
    match(asks_, taker, [](std::int64_t ask, std::int64_t limit) { return ask <= limit; }, result);
  } else {
    match(bids_, taker, [](std::int64_t bid, std::int64_t limit) { return bid >= limit; }, result);
  }

  if (taker.quantity > 0) {
    result.resting_quantity = taker.quantity;
    if (side == Side::Buy) {
      bids_[taker.price_ticks].push_back(taker);
    } else {
      asks_[taker.price_ticks].push_back(taker);
    }
    ++resting_count_;
  }
  return result;
}

bool OrderBook::cancel(std::uint64_t order_id) {
  const auto drop = [&](auto& ladder) {
    for (auto level = ladder.begin(); level != ladder.end(); ++level) {
      auto& queue = level->second;
      const auto found = std::find_if(queue.begin(), queue.end(),
                                      [&](const Order& order) { return order.id == order_id; });
      if (found == queue.end()) continue;
      queue.erase(found);
      --resting_count_;
      if (queue.empty()) ladder.erase(level);
      return true;
    }
    return false;
  };
  return drop(bids_) || drop(asks_);
}

Depth OrderBook::depth(std::size_t levels) const {
  Depth snapshot;
  const auto collect = [levels](const auto& ladder, std::vector<Level>& out) {
    for (const auto& entry : ladder) {
      if (out.size() >= levels) break;
      std::int64_t total = 0;
      for (const Order& order : entry.second) total += order.quantity;
      if (total > 0) out.push_back({entry.first, total});
    }
  };
  collect(bids_, snapshot.bids);
  collect(asks_, snapshot.asks);
  return snapshot;
}

std::optional<std::int64_t> OrderBook::best_bid_ticks() const {
  if (bids_.empty()) return std::nullopt;
  return bids_.begin()->first;
}

std::optional<std::int64_t> OrderBook::best_ask_ticks() const {
  if (asks_.empty()) return std::nullopt;
  return asks_.begin()->first;
}

}  // namespace velox
