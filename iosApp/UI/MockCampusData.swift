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

enum MockCampusData {
    static let snapshot = MockCampusSnapshot(
        currentUserName: "Alex Chen",
        availableBalanceMinorUnits: 12_450,
        peers: [
            MockPeer(id: "peer-jordan", displayName: "Jordan Lee"),
            MockPeer(id: "peer-sam", displayName: "Sam Patel"),
            MockPeer(id: "peer-riley", displayName: "Riley Nguyen"),
        ],
        transactions: [
            MockTransactionRow(
                id: "tx-1",
                counterparty: "Jordan Lee",
                amountMinorUnits: -1_250,
                status: "Completed",
                date: "Sep 24"
            ),
            MockTransactionRow(
                id: "tx-2",
                counterparty: "Campus Cafe",
                amountMinorUnits: -475,
                status: "Processing",
                date: "Sep 23"
            ),
            MockTransactionRow(
                id: "tx-3",
                counterparty: "Sam Patel",
                amountMinorUnits: 2_000,
                status: "Completed",
                date: "Sep 22"
            ),
            MockTransactionRow(
                id: "tx-4",
                counterparty: "Riley Nguyen",
                amountMinorUnits: -800,
                status: "Failed",
                date: "Sep 21"
            ),
            MockTransactionRow(
                id: "tx-5",
                counterparty: "Bookstore",
                amountMinorUnits: -3_299,
                status: "Completed",
                date: "Sep 18"
            ),
            MockTransactionRow(
                id: "tx-6",
                counterparty: "Jordan Lee",
                amountMinorUnits: 500,
                status: "Completed",
                date: "Sep 16"
            ),
        ]
    )
}
