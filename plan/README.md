# Active plan: account metadata, customers/vendors, recurring suggestions, review

Status: **paused** after Phases A and B (both built and unit tested; on-device checks pending). Phase C not started.
Last updated: 2026-10-17.

## How to resume

When Ravi says to resume this plan:

1. Read this whole file.
2. `git checkout feature/parties-review`. It holds Phases A and B (see "Branches"). Phase C starts from here on a new branch.
3. `git status` and `git log --oneline -5` to confirm the state matches this file.
4. Carry on from "Phase C" (or the on-device checks, whichever Ravi asks for).
5. Ask Ravi questions as clickable multiple-choice options, never as prose.

Standing rules for this project (from `CLAUDE.md` and Ravi's instructions):

- No personal data in code, comments or tests. Use placeholders such as account `1234`, `TEST LOCATION`. Never write Ravi's real account numbers, card digits, places or balances anywhere in the repo.
- Parser tests use the shared `ParserTestUtils` (`docs/parser-test-standards.md`) and `@TestFactory`, otherwise the cases never run.
- Commits I make on my own initiative end with `Co-Authored-By: Ravi Sharma (NAX IT Solutions) <ravi@naxits.com>`. Never add a Claude or Anthropic co-author line.
- "Commit this task: {note}" means commit with the note verbatim and no trailer.
- Do not commit or merge unasked, except the per-phase commits this plan already covers.

## The request (10 items, from Ravi's pasted plan)

1. **Bank name and custom icon.** Keep the bank name; a manually chosen account icon must win over the logo found from the bank name.
2. **Account tags.** Savings, Current, FD, RD and so on, as tags. No separate Personal/Business field.
3. **Account description.** Shown and editable on the account pages.
4. **One existing account** (Ravi named its last four digits; they are kept out of the repo on purpose). It is detected as Punjab & Sind Bank; correct it to Himachal Pradesh State Cooperative Bank, with the right logo, without touching other accounts.
5. **Custom account order.** Persisted, first account stays first until the user changes it.
6. **Customer on business transactions.** Pick or type a customer with autocomplete, add new, store on the transaction.
7. **Customer/vendor detection for SMS.** Suggest from history, phone, account number. Otherwise **Unknown**. Suggestions get a visible red **Review** badge, show how they were decided, can be corrected and confirmed, and the reviewed state is recorded.
8. **Recurring detection and category suggestions**, for all categories, not just salary. Combine account, party, description, monthly interval and amount (configurable 5-10% tolerance). Remember the user's category per pattern, suggest it next time, mark it Review, explain why, never silently assign an uncertain category.
9. **Auto-confirm after 3 months.** Pending reviews keep their suggested category and are marked auto-confirmed; track manual vs automatic; keep the history; applies to every category.
10. **Implementation and testing.** Reuse existing code, migrations that keep data, tests for each item, and a summary of changes, database modifications and tests at the end.

## Decisions taken with Ravi

- **Parties:** a new customers/vendors table, separate from Khata (`lend_borrow_persons`).
- **Himachal bank:** a new parser (not just a relabel). Sender is `HPSBNK`, which contains `PSB`, so the Punjab & Sind parser was claiming it.
- **Customer field:** shown on Add Transaction only when the chosen account has the **Business** tag.
- **Delivery:** 3 phases, one branch each, tested and committed before the next.
- **Assumptions I made (confirm if they come up):**
  - Party detection (item 7) runs only for SMS on Business-tagged accounts. Otherwise every personal SMS would get a red badge.
  - Himachal logo is a generated "HP / COOP BANK" monogram; swap in the official logo if Ravi supplies one (replace `app/src/main/res/drawable-nodpi/ic_brand_himachal_state_coop_bank.webp`).
  - The Himachal relabel is selected by evidence (all SMS-sourced transactions on the account come from `HPSBNK` senders), never by hard-coding the number.

## Branches

- `develop`: has the ATM-withdrawal feature merged (merge commit `b994f069`). Nothing from this plan is on `develop` yet.
- `feature/account-metadata`: **Phase A**, one commit (`feat: account icon, tags and description, and the Himachal bank fix`), branched from `develop`.
- `feature/parties-review`: **Phase B**, branched from Phase A (it needs the Business tag). Complete, committed, unit tested.
- Phase C is not started. Branch it from Phase B when ready: `feature/recurring-suggestions`.
- Merge order when all is verified: A, then B, then C, into `develop`. Nothing has been pushed.

## Database versions

| Version | Phase | Change | State |
|---|---|---|---|
| 69 | A | `account_meta(bank_name, account_last4, custom_icon_name, tags, description, updated_at)` | done, committed |
| 70 | B | `parties`, `transaction_reviews`, column `transactions.party_id` | written, compiles, migration SQL checked against `70.json` |
| 71 | C | `category_patterns(pattern_key, type, category, subcategory, count, last_amount, last_date, updated_at)` | not started |

Migration pattern: hand-written `MIGRATION_n_m` in `CashiroDatabase.kt`, SQL copied from the exported schema JSON in `app/schemas/...`, registered in `di/DatabaseModule.kt` (the live Hilt builder). Always diff the SQL against the new `NN.json` after a build.

## Phase A: DONE (branch `feature/account-metadata`)

- Himachal parser `HimachalStateCoopBankParser` (parser-core), registered before `PunjabSindBankParser`, which now rejects `HPSBNK`. 11 parser tests pass.
- One-time `BankRelabelBackfill` / `BankRelabeler` renames only accounts whose SMS all come from `HPSBNK`. Shared `AccountRenamer` + atomic `AccountRenameDao` now rename transactions, cards, bills, recurring entries, notes, order, hidden and main account too (the old edit-sheet rename only changed balances).
- `AccountMeta` (icon override, tags, description): entity/DAO/repository, `LocalAccountMeta` composition local fed by `AccountMetaViewModel` at the app root, `BrandIcon(accountLast4 = ...)` looks up the override, `IconProvider.getIconForTransaction(accountIconOverride = ...)` checks it first.
- Edit sheet: tags chips, description field, "Use bank logo" reset. `AccountCard` shows tags and description.
- Reorder sheet: Move up / Move down buttons plus existing drag. `AccountOrdering.move`.
- Backups carry `account_meta` (description stripped in non-full exports). Delete-all-data clears it.
- Tests added: `AccountMetaTest`, `AccountMetaRepositoryTest`, `BankRelabelerTest`, `BackupAccountMetaTest`, extra `AccountOrderingTest` cases, `HimachalStateCoopBankParserTest`.
- `CLAUDE.md` bank list updated (45 parsers).

**Not yet done for Phase A:** on-device verification (see "On-device checks").

## Phase B: DONE (branch `feature/parties-review`)

- Data: `parties` and `transaction_reviews` tables, `transactions.party_id`, database 70 with `MIGRATION_69_70` (SQL checked against `70.json`). Entities `PartyEntity`, `TransactionReviewEntity`, DAOs `PartyDao`, `TransactionReviewDao`.
- Logic (pure, tested): `CounterpartyExtractor`, `PartyMatcher`, `PartyLearning`, `PartyReviewDetector`, `ReviewActions` (`confirmParty`, `dismiss`), `ReviewService`, `PartyRepository`.
- SMS saves call `reviewService.onTransactionSaved(...)` after the repayment hook; only Business-tagged accounts get a party review (Unknown is reviewed too).
- Backups carry `parties` and `transaction_reviews` (replace, merge and selective paths; party and transaction ids remapped on merge; privacy levels strip names and notes). Delete-all-data clears them.
- Add Transaction: `PartyAutocompleteField` (suggestions while typing, "Add new customer"), shown only for a Business account on income, expense or credit entries; `AddTransactionUseCase.execute(partyId = ...)`.
- Review UI: red **Review** badge in `TransactionItem` (plain "Review" text in card style) fed by `LocalPendingReviews`; `TransactionPartySection` on the detail screen (both view and edit mode) with suggested value, reason, confidence, Confirm / Change / Dismiss and the audit lines; "Needs review" chip in the transactions filter sheet; Parties screen (add, edit, merge, archive) reachable from Settings.
- Tests added: `CounterpartyExtractorTest`, `PartyMatcherTest`, `PartyLearningTest`, `PartyReviewDetectorTest`, `ReviewActionsTest`, `PartyRepositoryTest`, `BackupPartiesTest`, with `PartyFakes.kt`. A test caught and fixed an e-mail address being read as a UPI id.
- At the end of Phase B: 544 app unit tests and the parser-core suite pass; `:app:assembleStandardDebug` builds.

Still to check by hand for Phase B: the Add screen customer field, the badge, the detail section and the Parties screen on the phone (see "On-device checks").

## Phase C: not started (branch `feature/recurring-suggestions` from Phase B)

Design is in the data model table above. Summary:

1. **Learning.** When the user changes a category (`TransactionDetailViewModel.saveChanges`) or confirms a CATEGORY review, write a `category_patterns` row keyed by party, counterparty identifier or normalised merchant, plus transaction type.
2. **`RecurringPatternDetector.suggest(new, patterns, history, now, tolerance)`**, pure. Combine: same party/identifier, description or reference similarity, recurring interval (weekly, monthly, quarterly, yearly with a few days of slack), amount within tolerance. Needs at least 2 earlier matches and a score above a threshold; otherwise suggest nothing and keep the parser category. Tolerance default 10%, setting 5 / 7.5 / 10% in `UserPreferencesRepository` and Settings.
3. **Applying it.** In `SmsTransactionProcessor` after rules and merchant mappings (an explicit user mapping or rule always wins and needs no review): set the category and file a CATEGORY review (PENDING) with a plain-words reason such as "3 earlier monthly payments to this account, amounts within 8%, categorised Salary". The `transaction_reviews` table already has `suggested_subcategory` for this.
4. **Auto-confirm (item 9).** Pure `ReviewAutoConfirmer.run(now)`: PENDING reviews created at or before `now - 3 months` become `AUTO_CONFIRMED` (`resolved_by = AUTO_3M`, `resolved_value` = suggested value); the transaction's category is not changed. `TransactionReviewDao.getPendingCreatedOnOrBefore` already exists. A daily `@HiltWorker` plus a scheduler copied from `WebhookSyncScheduler` (unique periodic work, `ExistingPeriodicWorkPolicy.UPDATE`), started from `CashiroApplication.onCreate`. Review history UI shows manual and automatic resolutions.
5. Tests: monthly salary, vendor in another category, amounts inside and outside tolerance, irregular interval, single earlier match (no suggestion), auto-confirm just under and just over 3 months, category unchanged, both resolution types kept.

## On-device checks

Installed on Ravi's phone (model `21091116I`) over USB on 2026-10-17 from `feature/parties-review`. `adb` is at `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` (not on PATH in PowerShell). The arm64 build is `app/build/outputs/apk/standard/debug/app-standard-arm64-v8a-debug.apk`. The database can be copied with `adb exec-out run-as com.naxits.paisatracker cat databases/pennywise_database` (plus the `-wal` and `-shm` files) into the scratchpad for read-only checks; the real data must stay local.

**Verified by comparing the database before and after the update:**
- Migrations 68 to 69 to 70 ran; `account_meta`, `parties`, `transaction_reviews` exist.
- The one-time Himachal relabel renamed the misdetected account (6 balance rows, same balances) and all 8 transactions from the Himachal sender, including 2 that had no account row (this is why the relabel also walks transactions, and why its flag is `bank_relabel_backfill_v2_done`). Every other account and transaction is identical to before. No crashes in the log.

**Still to check by hand (nothing automated can see the screens):**
- **Reorder:** move accounts with the arrows, restart the app, check the order persists and the first account stays first.
- **Tags, description, icon:** set them on an account; check the card and the detail page, that a custom icon beats the bank-name logo, and that "Use bank logo" resets it.
- **Himachal logo:** the account card shows the "HP / COOP BANK" monogram.
- **Business flow:** tag an account Business, add a transaction with a new and an existing customer (autocomplete), and look at the detail screen and the Parties screen under Settings.
- **Review flow:** replay a test SMS on the Business account to see Unknown or a suggested match with its reason and the red badge, then confirm, change and dismiss; try the "Needs review" filter.
- **Recurring (after Phase C):** replay three monthly test SMS and check the suggestion and badge; run the worker with a backdated review.

## Things noticed along the way (not part of this plan)

- `SanitizationUtils.TransactionEntity.sanitize()` does not copy `reference`, so UPI references are lost when a backup is restored (can cause duplicate detection to miss).
- `CashiroDatabase.getInstance()` lists migrations only up to 60 to 61; it is used by receivers without Hilt and would fail to open an older database first. The Hilt builder in `DatabaseModule.kt` is complete.
- About 37 other parser test classes use a plain `@Test` and never run their cases (they need `@TestFactory`).
- Importing an older backup whose JSON lacks newer list keys (for example net worth) may hit a null list, because Gson ignores Kotlin defaults for `DatabaseSnapshot`.

## Finish line

When all three phases are verified and merged, give Ravi the summary item 10 asks for (changes, database modifications, tests run) and offer a MINOR version bump (`app/build.gradle.kts`, currently 2.1.61-beta, code 94), then update `CLAUDE.md` and delete or archive this file.
