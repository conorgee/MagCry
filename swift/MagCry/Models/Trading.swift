import Foundation

// MARK: - Errors

enum TradingError: Error, LocalizedError {
    case invalidSpread(Int)
    case unknownPlayer(String)
    case selfTrade
    case settlePhase

    var errorDescription: String? {
        switch self {
        case .invalidSpread(let s): return "Spread must be exactly 2, got \(s)"
        case .unknownPlayer(let id): return "Unknown player: \(id)"
        case .selfTrade: return "Cannot trade with yourself"
        case .settlePhase: return "Cannot trade during settlement"
        }
    }
}

// MARK: - Functions

/// Validate that a bid/ask pair has spread of exactly 2.
func validateQuote(bid: Int, ask: Int) throws -> Quote {
    let spread = ask - bid
    guard spread == 2 else { throw TradingError.invalidSpread(spread) }
    return Quote(bid: bid, ask: ask)
}

/// Execute a trade directly at a specified price (V2 mechanic).
/// This is the primary trade function — no standing quote needed.
@discardableResult
func executeTradeDirectly(
    state: GameState,
    buyer: String,
    seller: String,
    price: Int
) throws -> Trade {
    guard buyer != seller else { throw TradingError.selfTrade }
    guard state.phase != .settle else { throw TradingError.settlePhase }
    guard state.playerIDs.contains(buyer) else { throw TradingError.unknownPlayer(buyer) }
    guard state.playerIDs.contains(seller) else { throw TradingError.unknownPlayer(seller) }

    let trade = Trade(buyer: buyer, seller: seller, price: price, phase: state.phase)
    state.trades.append(trade)
    return trade
}
