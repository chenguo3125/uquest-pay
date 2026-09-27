import Foundation
import Shared

enum PaymentError: LocalizedError {
    case message(String)

    var errorDescription: String? {
        switch self {
        case .message(let text):
            return text
        }
    }
}

final class FoundationClock: NSObject, Clock {
    func nowMillis() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }
}

final class PaymentService {
    private let session: PaymentSession

    init(session: PaymentSession) {
        self.session = session
    }

    convenience init() {
        self.init(session: PaymentSession(clock: FoundationClock(), ids: RandomIdGenerator.shared))
    }

    func snapshot() -> MockCampusSnapshot {
        let raw = session.snapshot()
        let transactions = (raw.transactions as NSArray).compactMap { $0 as? SessionTransactionRow }
        let peers = (raw.peers as NSArray).compactMap { $0 as? SessionPeer }
        let rows = transactions.map { row -> MockTransactionRow in
            MockTransactionRow(
                id: row.id,
                counterparty: row.counterparty,
                amountMinorUnits: Int(row.amountMinorUnits),
                status: row.status,
                date: Self.formatDate(millis: row.createdAtMillis)
            )
        }
        return MockCampusSnapshot(
            currentUserName: raw.currentUserName,
            availableBalanceMinorUnits: Int(raw.availableBalanceMinorUnits),
            peers: peers.map { peer in
                MockPeer(id: peer.walletId, displayName: peer.displayName)
            },
            transactions: rows
        )
    }

    func send(toWalletId: String, amountText: String, note: String?) -> Result<Void, PaymentError> {
        let result = session.send(toWalletId: toWalletId, amountMajor: amountText, note: note)
        if result is SendResult.Ok {
            return .success(())
        }
        if let err = result as? SendResult.Err {
            return .failure(.message(err.message))
        }
        return .failure(.message("Could not complete this transfer"))
    }

    private static func formatDate(millis: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(millis) / 1000)
        return dateFormatter.string(from: date)
    }

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = "MMM d"
        return formatter
    }()
}
