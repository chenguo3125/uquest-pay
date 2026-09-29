# uQuest Pay Architecture Rules

- Do not place business logic inside UIKit view controllers.
- View controllers should coordinate UI state only.
- Financial calculations must live in KMP.
- Never use Double for monetary arithmetic.
- Prefer integer minor units or a decimal-safe representation.
- All transaction state transitions must be explicit.
- AI-generated values must be validated before entering financial logic.
- Core Data must not be accessed directly from view controllers.
- Repository changes must be small and testable.
- Add tests for every financial rule.
- Do not introduce dependencies without explaining why.