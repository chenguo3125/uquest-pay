import UIKit

final class TransactionRowCell: UITableViewCell {
    static let reuseIdentifier = "TransactionRowCell"

    private let counterpartyLabel = UILabel()
    private let dateLabel = UILabel()
    private let amountLabel = UILabel()
    private let statusLabel = UILabel()

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        configure()
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func bind(_ row: MockTransactionRow) {
        counterpartyLabel.text = row.counterparty
        dateLabel.text = row.date
        amountLabel.text = row.amountText
        statusLabel.text = row.status
        amountLabel.textColor = row.amountMinorUnits < 0
            ? .label
            : UIColor(red: 0.10, green: 0.52, blue: 0.29, alpha: 1)
    }

    private func configure() {
        selectionStyle = .none
        accessoryType = .none

        counterpartyLabel.font = .preferredFont(forTextStyle: .body)
        dateLabel.font = .preferredFont(forTextStyle: .caption1)
        dateLabel.textColor = .secondaryLabel
        amountLabel.font = .preferredFont(forTextStyle: .body)
        amountLabel.textAlignment = .right
        statusLabel.font = .preferredFont(forTextStyle: .caption1)
        statusLabel.textColor = .secondaryLabel
        statusLabel.textAlignment = .right

        let left = UIStackView(arrangedSubviews: [counterpartyLabel, dateLabel])
        left.axis = .vertical
        left.spacing = 2

        let right = UIStackView(arrangedSubviews: [amountLabel, statusLabel])
        right.axis = .vertical
        right.spacing = 2
        right.alignment = .trailing

        let row = UIStackView(arrangedSubviews: [left, right])
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = 12
        row.translatesAutoresizingMaskIntoConstraints = false

        contentView.addSubview(row)
        NSLayoutConstraint.activate([
            row.topAnchor.constraint(equalTo: contentView.layoutMarginsGuide.topAnchor),
            row.bottomAnchor.constraint(equalTo: contentView.layoutMarginsGuide.bottomAnchor),
            row.leadingAnchor.constraint(equalTo: contentView.layoutMarginsGuide.leadingAnchor),
            row.trailingAnchor.constraint(equalTo: contentView.layoutMarginsGuide.trailingAnchor),
        ])
    }
}
