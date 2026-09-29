# uQuest Pay

## Objective

Build a production-inspired campus payment prototype demonstrating:

- native iOS development
- UIKit
- Kotlin Multiplatform
- deterministic financial logic
- concurrency
- Core Data
- AI-assisted development
- AI-powered receipt parsing
- natural-language transaction search

## Architecture Principles

1. Financial calculations must be deterministic.
2. UI must never directly modify wallet balances.
3. Transaction state transitions must be validated.
4. Shared financial logic belongs in KMP.
5. Local persistence belongs in the iOS data layer.
6. AI output must be treated as untrusted input.
7. Every AI-derived financial value must be validated before entering the ledger.

## MVP

The MVP must support:

- wallet balance
- peer-to-peer transfer
- transaction history
- transaction state machine
- local persistence
- simulated network delay
- failure/retry
- concurrent transaction requests
- receipt parsing
- natural-language transaction search (can be buildt later)

## Non-goals

- real money
- real payment gateway
- real bank integration
- production authentication
- actual financial transactions