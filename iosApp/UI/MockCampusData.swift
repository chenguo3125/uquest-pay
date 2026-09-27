import Foundation

struct MockPeer: Equatable {
    let id: String
    let displayName: String
}

struct MockTransactionRow: Equatable {
    let id: String
    let counterparty: String
    let amountMinorUnits: Int
    let status: String
    let date: String

    var amountText: String {
        MockMoneyFormat.signedUQC(minorUnits: amountMinorUnits)
    }
}

struct MockCampusSnapshot {
    let currentUserName: String
    let availableBalanceMinorUnits: Int
    let peers: [MockPeer]
    let transactions: [MockTransactionRow]

    var availableBalanceText: String {
        MockMoneyFormat.uqc(minorUnits: availableBalanceMinorUnits)
    }

    var recentTransactions: [MockTransactionRow] {
        Array(transactions.prefix(3))
    }
}

enum MockMoneyFormat {
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
