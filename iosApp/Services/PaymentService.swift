import Foundation
import Shared

enum PaymentStatus: String {
    case completed
    case processing
    case failed
}

struct PaymentFailure: Equatable {
    let code: String
    let message: String
    let availableMinorUnits: Int64?
    let requiredMinorUnits: Int64?
    let existingTransactionId: String?
}

struct PaymentOutcome: Equatable {
    let transactionId: String?
    let status: PaymentStatus
    let failure: PaymentFailure?
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
        self.init(
            session: CampusDemo.shared.session(clock: FoundationClock(), ids: RandomIdGenerator.shared)
        )
    }

    func snapshot() -> CampusSnapshot {
        let raw = session.snapshot()
        let transactions = (raw.transactions as NSArray).compactMap { $0 as? SessionTransactionRow }
        let peers = (raw.peers as NSArray).compactMap { $0 as? SessionPeer }
        let rows = transactions.map { row -> TransactionRow in
            TransactionRow(
                id: row.id,
                counterparty: row.counterparty,
                amountMinorUnits: Int(row.amountMinorUnits),
                status: row.status,
                date: Self.formatDate(millis: row.createdAtMillis)
            )
        }
        return CampusSnapshot(
            currentUserName: raw.currentUserName,
            availableBalanceMinorUnits: Int(raw.availableBalanceMinorUnits),
            peers: peers.map { peer in
                Peer(id: peer.walletId, displayName: peer.displayName)
            },
            transactions: rows
        )
    }

    func send(
        toWalletId: String,
        amountText: String,
        note: String?,
        idempotencyKey: String
    ) -> PaymentOutcome {
        mapResult(session.send(toWalletId: toWalletId, amountMajor: amountText, note: note, idempotencyKey: idempotencyKey))
    }

    func retry(transactionId: String) -> PaymentOutcome {
        mapResult(session.retry(transactionId: transactionId))
    }

    private func mapResult(_ result: SendResult) -> PaymentOutcome {
        if let completed = result as? SendResult.Completed {
            return PaymentOutcome(transactionId: completed.transactionId, status: .completed, failure: nil)
        }
        if let processing = result as? SendResult.Processing {
            return PaymentOutcome(transactionId: processing.transactionId, status: .processing, failure: nil)
        }
        if let failed = result as? SendResult.Failed {
            return PaymentOutcome(
                transactionId: failed.transactionId,
                status: .failed,
                failure: mapFailure(failed.reason)
            )
        }
        return PaymentOutcome(
            transactionId: nil,
            status: .failed,
            failure: PaymentFailure(
                code: "Unknown",
                message: "Could not complete this transfer",
                availableMinorUnits: nil,
                requiredMinorUnits: nil,
                existingTransactionId: nil
            )
        )
    }

    private func mapFailure(_ reason: FailureReason) -> PaymentFailure {
        if let funds = reason as? FailureReason.InsufficientFunds {
            return PaymentFailure(
                code: "InsufficientFunds",
                message: "Insufficient funds",
                availableMinorUnits: funds.available.minorUnits,
                requiredMinorUnits: funds.required.minorUnits,
                existingTransactionId: nil
            )
        }
        if reason is FailureReason.InvalidAmount {
            return PaymentFailure(code: "InvalidAmount", message: "Enter a valid amount", availableMinorUnits: nil, requiredMinorUnits: nil, existingTransactionId: nil)
        }
        if reason is FailureReason.SameAccount {
            return PaymentFailure(code: "SameAccount", message: "You cannot send to yourself", availableMinorUnits: nil, requiredMinorUnits: nil, existingTransactionId: nil)
        }
        if reason is FailureReason.UnknownCurrency {
            return PaymentFailure(code: "UnknownCurrency", message: "Unsupported currency", availableMinorUnits: nil, requiredMinorUnits: nil, existingTransactionId: nil)
        }
        if reason is FailureReason.WalletNotFound {
            return PaymentFailure(code: "WalletNotFound", message: "Recipient not found", availableMinorUnits: nil, requiredMinorUnits: nil, existingTransactionId: nil)
        }
        if let conflict = reason as? FailureReason.IdempotencyConflict {
            return PaymentFailure(
                code: "IdempotencyConflict",
                message: "This transfer was already submitted",
                availableMinorUnits: nil,
                requiredMinorUnits: nil,
                existingTransactionId: conflict.existingTransactionId
            )
        }
        return PaymentFailure(
            code: "CouldNotComplete",
            message: "Could not complete this transfer",
            availableMinorUnits: nil,
            requiredMinorUnits: nil,
            existingTransactionId: nil
        )
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
