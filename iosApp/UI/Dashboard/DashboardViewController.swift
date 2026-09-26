import UIKit

final class DashboardViewController: UIViewController {
    private let snapshot: MockCampusSnapshot

    private let balanceTitleLabel = UILabel()
    private let balanceValueLabel = UILabel()
    private let sendButton = UIButton(type: .system)
    private let recentHeaderLabel = UILabel()
    private let seeAllButton = UIButton(type: .system)
    private let tableView = UITableView(frame: .zero, style: .plain)
    private var tableHeightConstraint: NSLayoutConstraint?

    init(snapshot: MockCampusSnapshot) {
        self.snapshot = snapshot
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Dashboard"
        navigationItem.largeTitleDisplayMode = .always
        view.backgroundColor = .systemBackground
        buildLayout()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        tableView.layoutIfNeeded()
        tableHeightConstraint?.constant = tableView.contentSize.height
    }

    private func buildLayout() {
        balanceTitleLabel.text = "Available balance"
        balanceTitleLabel.font = .preferredFont(forTextStyle: .subheadline)
        balanceTitleLabel.textColor = .secondaryLabel

        balanceValueLabel.text = snapshot.availableBalanceText
        balanceValueLabel.font = .preferredFont(forTextStyle: .largeTitle)
        balanceValueLabel.adjustsFontForContentSizeCategory = true

        let userLabel = UILabel()
        userLabel.text = snapshot.currentUserName
        userLabel.font = .preferredFont(forTextStyle: .footnote)
        userLabel.textColor = .secondaryLabel

        let balanceStack = UIStackView(arrangedSubviews: [balanceTitleLabel, balanceValueLabel, userLabel])
        balanceStack.axis = .vertical
        balanceStack.spacing = 4

        let card = UIView()
        card.backgroundColor = .secondarySystemBackground
        card.layer.cornerRadius = 16
        card.layoutMargins = UIEdgeInsets(top: 20, left: 20, bottom: 20, right: 20)
        card.addSubview(balanceStack)
        balanceStack.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            balanceStack.topAnchor.constraint(equalTo: card.layoutMarginsGuide.topAnchor),
            balanceStack.leadingAnchor.constraint(equalTo: card.layoutMarginsGuide.leadingAnchor),
            balanceStack.trailingAnchor.constraint(equalTo: card.layoutMarginsGuide.trailingAnchor),
            balanceStack.bottomAnchor.constraint(equalTo: card.layoutMarginsGuide.bottomAnchor),
        ])

        var sendConfig = UIButton.Configuration.filled()
        sendConfig.title = "Send Money"
        sendConfig.cornerStyle = .large
        sendButton.configuration = sendConfig
        sendButton.addTarget(self, action: #selector(openTransfer), for: .touchUpInside)

        recentHeaderLabel.text = "Recent Transactions"
        recentHeaderLabel.font = .preferredFont(forTextStyle: .headline)

        seeAllButton.setTitle("See all", for: .normal)
        seeAllButton.setContentHuggingPriority(.required, for: .horizontal)
        seeAllButton.addTarget(self, action: #selector(openHistory), for: .touchUpInside)

        let headerRow = UIStackView(arrangedSubviews: [recentHeaderLabel, seeAllButton])
        headerRow.axis = .horizontal
        headerRow.alignment = .center

        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableView.dataSource = self
        tableView.delegate = self
        tableView.isScrollEnabled = false
        tableView.separatorInset = .zero
        tableView.register(TransactionRowCell.self, forCellReuseIdentifier: TransactionRowCell.reuseIdentifier)
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 64

        let content = UIStackView(arrangedSubviews: [card, sendButton, headerRow, tableView])
        content.axis = .vertical
        content.spacing = 20
        content.translatesAutoresizingMaskIntoConstraints = false

        let scrollView = UIScrollView()
        scrollView.alwaysBounceVertical = true
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(content)
        view.addSubview(scrollView)

        let height = tableView.heightAnchor.constraint(equalToConstant: 1)
        tableHeightConstraint = height

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            content.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 16),
            content.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: 20),
            content.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -20),
            content.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -24),
            height,
        ])
    }

    @objc private func openTransfer() {
        let transfer = TransferViewController(snapshot: snapshot)
        navigationController?.pushViewController(transfer, animated: true)
    }

    @objc private func openHistory() {
        let history = TransactionsViewController(snapshot: snapshot)
        navigationController?.pushViewController(history, animated: true)
    }
}

extension DashboardViewController: UITableViewDataSource, UITableViewDelegate {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        snapshot.recentTransactions.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        guard let cell = tableView.dequeueReusableCell(
            withIdentifier: TransactionRowCell.reuseIdentifier,
            for: indexPath
        ) as? TransactionRowCell else {
            return UITableViewCell()
        }
        cell.bind(snapshot.recentTransactions[indexPath.row])
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        openHistory()
    }
}
