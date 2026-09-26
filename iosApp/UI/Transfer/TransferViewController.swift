import UIKit

final class TransferViewController: UIViewController {
    private let snapshot: MockCampusSnapshot
    private var selectedPeer: MockPeer?

    private let recipientButton = UIButton(type: .system)
    private let amountField = UITextField()
    private let noteField = UITextField()
    private let sendButton = UIButton(type: .system)

    init(snapshot: MockCampusSnapshot) {
        self.snapshot = snapshot
        self.selectedPeer = snapshot.peers.first
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Send Money"
        navigationItem.largeTitleDisplayMode = .never
        view.backgroundColor = .systemBackground
        buildLayout()
        refreshRecipientTitle()
    }

    private func buildLayout() {
        let recipientLabel = sectionLabel("Recipient")
        configureMenuButton()

        let amountLabel = sectionLabel("Amount (UQC)")
        amountField.placeholder = "0.00"
        amountField.keyboardType = .decimalPad
        amountField.borderStyle = .roundedRect
        amountField.accessibilityIdentifier = "transfer-amount"

        let noteLabel = sectionLabel("Note")
        noteField.placeholder = "Optional"
        noteField.borderStyle = .roundedRect

        var sendConfig = UIButton.Configuration.filled()
        sendConfig.title = "Send"
        sendConfig.cornerStyle = .large
        sendButton.configuration = sendConfig
        sendButton.addTarget(self, action: #selector(sendTapped), for: .touchUpInside)

        let form = UIStackView(arrangedSubviews: [
            recipientLabel,
            recipientButton,
            amountLabel,
            amountField,
            noteLabel,
            noteField,
            sendButton,
        ])
        form.axis = .vertical
        form.spacing = 12
        form.setCustomSpacing(20, after: recipientButton)
        form.setCustomSpacing(20, after: amountField)
        form.setCustomSpacing(28, after: noteField)
        form.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(form)

        NSLayoutConstraint.activate([
            form.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 20),
            form.leadingAnchor.constraint(equalTo: view.layoutMarginsGuide.leadingAnchor),
            form.trailingAnchor.constraint(equalTo: view.layoutMarginsGuide.trailingAnchor),
            recipientButton.heightAnchor.constraint(equalToConstant: 48),
            sendButton.heightAnchor.constraint(equalToConstant: 50),
        ])
    }

    private func sectionLabel(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = .preferredFont(forTextStyle: .subheadline)
        label.textColor = .secondaryLabel
        return label
    }

    private func configureMenuButton() {
        var config = UIButton.Configuration.gray()
        config.cornerStyle = .medium
        config.contentInsets = NSDirectionalEdgeInsets(top: 12, leading: 12, bottom: 12, trailing: 12)
        recipientButton.configuration = config
        recipientButton.contentHorizontalAlignment = .leading
        recipientButton.menu = UIMenu(
            children: snapshot.peers.map { peer in
                UIAction(title: peer.displayName) { [weak self] _ in
                    self?.selectedPeer = peer
                    self?.refreshRecipientTitle()
                }
            }
        )
        recipientButton.showsMenuAsPrimaryAction = true
    }

    private func refreshRecipientTitle() {
        var config = recipientButton.configuration ?? .gray()
        config.title = selectedPeer?.displayName ?? "Choose a recipient"
        recipientButton.configuration = config
    }

    @objc private func sendTapped() {
        let alert = UIAlertController(
            title: "Payment not connected",
            message: "Transfers are not wired yet. This screen is a UI shell.",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}
