import Foundation

struct Peer: Equatable {
    let id: String
    let displayName: String
}

struct TransactionRow: Equatable {
    let id: String
    let counterparty: String
    let amountMinorUnits: Int
    let status: String
    let date: String

    var amountText: String {
        MoneyFormat.signedUQC(minorUnits: amountMinorUnits)
    }
}

struct CampusSnapshot {
    let currentUserName: String
    let availableBalanceMinorUnits: Int
    let peers: [Peer]
    let transactions: [TransactionRow]

    var availableBalanceText: String {
        MoneyFormat.uqc(minorUnits: availableBalanceMinorUnits)
    }

    var recentTransactions: [TransactionRow] {
        Array(transactions.prefix(3))
    }
}

enum MoneyFormat {
    static func uqc(minorUnits: Int) -> String {
        let negative = minorUnits < 0
        let absolute = abs(minorUnits)
        let major = absolute / 100
        let fraction = absolute % 100
        let fractionText = fraction < 10 ? "0\(fraction)" : "\(fraction)"
        let body = "\(major).\(fractionText) UQC"
        return negative ? "-\(body)" : body
    }

    static func signedUQC(minorUnits: Int) -> String {
        if minorUnits > 0 {
            return "+\(uqc(minorUnits: minorUnits))"
        }
        return uqc(minorUnits: minorUnits)
    }
}
