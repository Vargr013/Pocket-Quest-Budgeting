# Firebase integration contract

Status: **Draft for review - slice 1 of 10**

Checked: 13 October 2026, checkout `7f3d056` (`Updated graphs`)

Scope: documentation only. All new fields, paths and functions below are proposals, not implemented behaviour or recorded team/lecturer approval.

Slice 2 update, 14 October 2026: Firestore was confirmed for emulator/CI work only; real project/client configuration remains unavailable. This draft existed locally but was untracked and hidden by `*.md`; slice 2 exposes it for review without staging it. See `FIREBASE_SETUP.md` for the actual initial rule boundary and reproducible commands. D2/D3 and later policy decisions remain unresolved; the original slice 1 review below is historical.

## Current implementation and assessment

I checked the live checkout before writing this contract. Git was clean, and no applicable `AGENTS.md` was found in the repository or its parent folders. There is no earlier Firebase slice to depend on: no Firebase SDK/configuration, internet permission, sync queue, checked-in test sources or GitHub Actions workflow is present.

- `data/AppDatabase.kt` registers users, categories, expenses and budget goals at Room v4. `DatabaseProvider.kt` has migrations 1→2→3→4; exported schemas 1, 2 and 4 are tracked, but 3 is missing.
- `UserDao.kt` still compares plaintext passwords. `PocketQuestNavigation.kt` keeps a local `userId`, resets navigation/drafts with `key(userId)`, and supplies that ID to financial screens. Defaults are inserted through `initializeUserCategories` once per local account.
- Category and expense DAOs scope financial access by `userId`. Category deletion currently blocks any expense reference. Monthly goals are looked up by user and exact date range; the table has no unique month constraint.
- Camera/gallery receipts are copied into private `filesDir/receipts`; camera drafts use `cacheDir/receipt-captures` and FileProvider. `receiptImageUri` is an absolute file path, despite its name. The viewer opens `File(path)`. The copy helper gives files a `.jpg` extension without converting gallery bytes; file extension cannot prove image type. Expense deletion does not schedule file cleanup.
- Recent graph work is present. `DailySpendScreen.kt` and `CategorySpendScreen.kt` query user-scoped Room data. Dashboard category progress and monthly goals also use Room. Their visual correctness and full rubric coverage were not tested here.
- Savings add/update/remove handlers are empty; Dashboard savings figures are fixed text. Achievements show fixed streak/badge progress. There are no persisted savings or earned-achievement entities/DAOs. `UpdateSavingGoalScreen` has no navigation route. These screens are evidence of presentation work, not completed features.

The brief requires username/password registration and login, local data, expense photos, monthly total budgets and category limits, period-based expense/category views, graphs/progress, gamification and cross-device online data. Part 2 includes expense start/end times and both overall spending limits. Part 3 requires Firebase reads/writes, lecturer-feedback work, two own features from the Part 1 design, final assets, automated tests/GitHub Actions and a physical-phone video showing online data with a voiceover. This contract covers the shared data portion only; no lecturer feedback or Part 1 feature approval was supplied (The Independent Institute of Education, 2026a, pp.4, 7, 9-10, 20-21).

## Selected direction and open decisions

The recommended database is **Cloud Firestore, Standard edition**, with documents below `users/{uid}`. Keep Compose reading Room; explicit mapping functions move agreed fields between Room and cloud. Firestore caching does not synchronise Room, and its normal competing-write policy is last-write-wins, so the coordinator needs its own revision checks (Google, 2026l).

Use **Firebase Authentication's Email/Password provider**, preserving a username/password form. I recommend a deterministic username-to-email alias to avoid a public username directory or an extra lookup backend. Firebase's provider accepts email/password credentials; the alias mapping is our proposal, not a Firebase username provider (Google, 2026n).

Proposed mapping: trim the username as today, preserve its exact case and UTF-8 bytes, encode those bytes as lowercase hexadecimal, and append `@<agreed-alias-domain>`. For example, `Alex` produces local part `416c6578`; `alex` produces `616c6578`, so Firebase email case handling does not merge those usernames. Apply the same function on every installation. Propose 1–32 UTF-8 bytes after trimming, an immutable username, and no silent lowercasing, truncation or rename. Existing longer names need an explicit migration choice. The domain is a placeholder; it has not been selected or registered. Aliases have no real inbox, so email verification/reset cannot be promised. Successful Firebase credential authentication verifies control of the UID, not ownership of an email inbox.

Store receipt images in **authenticated Cloud Storage for Firebase**, with only an object path in Firestore and a separate private local file. Cloud Storage requires the **Blaze pay-as-you-go plan**; a possible no-cost allowance does not remove that billing prerequisite. No billing, bucket, project or rules deployment was authorised or performed (Google, 2026t).

The user confirmed that no project/contact, location, authorised billing or named feature owners are agreed yet.

| Decision still needed | Recommendation / exact missing detail | Dependent slices |
|---|---|---|
| D1: database and setup | Confirm Firestore, Firebase project ID, responsible contact, member access, Android client configuration, database and bucket locations. The brief does not specify Firestore versus Realtime Database. | 2; live work in 3–10 |
| D2: username authentication | Agree alias domain, encoding/byte limit, case-sensitive names, immutable usernames, longer-name migration and recovery policy. A real-email alternative needs a separately agreed trusted username lookup; email-only login does not preserve the brief. Lecturer clarification is needed if username behaviour is changed. | 3; account-dependent work in 4–10 |
| D3: local-account transition | Agree how an unlinked legacy account proves local control, when plaintext credentials are removed for unlinked rows, and recovery if a username is already owned by another UID. Do not remove the only local proof before that transition is settled. | 3 and initial import in 6 |
| D4: receipts | Responsible billing holder must decide whether Blaze is feasible and explicitly authorise paid setup. Agree image policy proposed below, or approve a protected alternative and revise this contract. | Live Storage in 2, receipts in 8, receipt demonstration in 10 |
| D5: shared data policy | Agree category archiving, defaults, duplicate-name review, retained mutation acknowledgements/tombstones and the proposed new validation limits. | 4–8 |
| D6: extra features | Assign savings/achievement owners; obtain persisted field/mutation contracts and confirm the two Part 1 own features. | 9 and full acceptance in 10 |

## Identity and exact record fields

Firebase UID is the cloud owner. Room `UserEntity.id: Long` remains the local owner and may differ on another device. Propose `firebaseUid: String?` on the local account, unique when non-null: one UID maps to one local row and one local row to one UID. Never use username matching as permission to merge data. Upload only explicit fields; neither `password` nor numeric local IDs nor device file paths belongs in cloud documents.

Categories and expenses get a lowercase UUID `cloudId: String`, allocated and stored before their first queued write/import. Keep Room numeric primary keys. Enforce unique `(userId, cloudId)` mappings. The cloud document ID is that ID, not an extra payload field. Cloud expense `categoryId` is a category cloud ID; download categories first and translate it to the same user's local category ID. Unknown references stay pending/reviewable, never fall back to another user's row or a category matched only by name.

Use `users/{uid}/categories/{cloudId}`, `users/{uid}/expenses/{cloudId}` and `users/{uid}/monthlyGoals/{YYYY-MM}`. Monthly goal `cloudId` is its month key, with one document per UID/month. Reconcile existing duplicate local month rows explicitly; `getForMonth` currently selects the highest local ID, but that does not justify discarding other rows.

All listed cloud fields are required; nullable fields must be present as `null` when absent. Reject unknown schema versions/fields instead of filling financial values with zero. Kotlin `Long` maps to Firestore signed 64-bit integer; do not use floating-point amounts (Google, 2026v).

### Categories

| Existing Room field | Cloud field / type / nullable | Validation or mapping |
|---|---|---|
| `id: Long` | Document ID / String / no | Keep local ID; use stable cloud ID. |
| `userId: Long` | UID in path / String / no | Map to authenticated owner; do not upload local number. |
| `name: String` | `name` / String / no | Trimmed, nonblank. Proposed maximum 100 Unicode code points; there is no current length cap. |
| `minMonthlyBudget: Long = 0` | `minimumCents` / Long / no | `0..Long.MAX_VALUE`; 0 means unset. |
| `maxMonthlyBudget: Long = 0` | `maximumCents` / Long / no | Same range/unset semantics; `minimumCents <= maximumCents`. A positive minimum with zero maximum remains invalid, matching the DAO. |

Current create/rename rejects same-user names using trimmed Kotlin `equals(ignoreCase = true)`. Keep that local check. UUIDs do not enforce cloud name uniqueness: concurrent same-name creates retain separate records and need duplicate review, with no automatic merge or expense reassignment. This proposal needs D5 agreement.

### Expenses

| Existing Room field | Cloud field / type / nullable | Validation or mapping |
|---|---|---|
| `id: Long` | Document ID / String / no | Stable UUID; keep numeric local key. |
| `userId: Long` | UID in path / String / no | Authenticated owner. |
| `categoryId: Long` | `categoryId` / String / no | Translate through same-user category mapping. New/changed assignments require an active category. |
| `amount: Long` | `amountCents` / Long / no | `1..Long.MAX_VALUE`, whole cents. |
| `date: String` | `date` / String / no | Actual valid Gregorian `YYYY-MM-DD`, year 0001–9999. No timezone conversion. |
| `startTime: String` | `startTime` / String / no | `HH:mm`, 24-hour, `00:00..23:59`. |
| `endTime: String` | `endTime` / String / no | Same format; `endTime >= startTime` on the same date. Overnight ranges remain unsupported. |
| `description: String` | `description` / String / no | Trimmed, nonblank. Proposed maximum 2,000 Unicode code points; no current length cap. |
| `receiptImageUri: String? = null` | Local only / String / yes | Keep private absolute path for the existing viewer; missing local bytes do not imply no cloud receipt. |
| No existing remote field | `receiptObjectPath` / String / yes | Null until upload/reference publication succeeds, or after explicit removal. Must match the owner/expense path below. |

### Overall monthly spending goals

| Existing Room field | Cloud field / type / nullable | Validation or mapping |
|---|---|---|
| `id: Long` | Document ID / String / no | `YYYY-MM` month key, not the Room number. |
| `userId: Long` | UID in path / String / no | Authenticated owner. |
| `minimumAmount: Long` | `minimumCents` / Long / no | `0..Long.MAX_VALUE`; zero is a valid spending goal. |
| `maximumAmount: Long` | `maximumCents` / Long / no | Same range; `maximumCents >= minimumCents`. |
| `startDate: String` | `startDate` / String / no | First day of the document's month. |
| `endDate: String` | `endDate` / String / no | Last valid day of that same month, inclusive. |

`BudgetGoalEntity` is an overall spending goal, not a savings goal. Existing history/monthly-goal ranges include both endpoints. `CategoryDao.getMonthProgress` instead uses an exclusive next-month boundary; mapping must preserve that query distinction.

Money input stays dot-separated with at most two decimal places; display stays rand formatting. For example, `12.34` becomes `1234L`. No currency conversion is proposed. Reject overflow, impossible dates and new-policy violations before upload; retain invalid legacy data for correction instead of truncating or deleting it.

### Shared cloud metadata and local state

Each category, expense and monthly goal adds these fields, including retained deletion documents:

| Field | Cloud type / nullable | Rule |
|---|---|---|
| `schemaVersion` | Long / no | `1L` for this proposed contract; separate from Room's version. |
| `revision` | Long / no | First accepted create is 1; each accepted mutation increases by exactly 1. Never based on device time. Reject overflow. |
| `lastMutationId` | String / no | UUID of the accepted mutation; retries keep that same ID and payload. |
| `updatedAt` | Timestamp / no | Server-assigned commit time; locally null until acknowledged. Not a budget date or conflict tie-breaker. |
| `isDeleted` | Boolean / no | False for active records, true for retained tombstones/archived categories. |
| `deletedAt` | Timestamp / yes | Null when active; server timestamp when marked deleted. |

Retain business fields on tombstones. No automatic purge or undelete is proposed. Explicit recreation uses a new UUID; reopening a deleted month's goal requires a separately agreed restore operation, not an implicit `revision = 1` overwrite.

Propose local metadata alongside each mapped row: `cloudId: String`, `cloudRevision: Long = 0`, `localSequence: Long = 0`, `isDeleted: Boolean = false`, `updatedAtEpochMillis: Long?`, `deletedAtEpochMillis: Long?`, `lastMutationId: String?`, and expense `receiptObjectPath: String?`. Map server timestamps to nullable epoch milliseconds for Room; this does not change business date/time strings. These are future migration requirements, not additions to v4.

Proposed `users/{uid}` setup document has only `schemaVersion: Long = 1`, `username: String` and `categoriesInitialized: Boolean = false`, all non-null. Username validation follows D2; the setup flag is monotonic false→true. Room's existing `categoriesInitialized` remains local. Auth passwords stay with Firebase; no password or alias-email copy is needed in Firestore.

## Planned entry points and sync protocol

Use one `PocketQuestSyncCoordinator` with small explicit mapping functions. The names below are planned contracts; none exists yet. Each call captures an immutable `SyncAccount(localUserId: Long, uid: String, sessionGeneration: Long)` rather than reading a changing global account midway through a task.

For existing features, planned `CategoryDraft` contains `localId: Long?` (null for create), `name: String`, `minimumCents: Long`, `maximumCents: Long`. Planned `ExpenseDraft` contains `localId: Long?`, `categoryId: Long`, `amountCents: Long`, `date: String`, `startTime: String`, `endTime: String`, `description: String`, `receiptLocalPath: String?`, `receiptChange: String` (`KEEP`, `REPLACE`, `REMOVE`). Replacement requires readable local bytes; `KEEP` preserves a remote reference even without a cached file. A null file path alone never means removal. These use the field validation above. Caller IDs are local Long IDs; `monthKey`, `mutationId` and `receiptJobId` are Strings. Save calls return only local-save success/failure; sync status is separate.

| Planned entry point | Caller and responsibility |
|---|---|
| `onSignedIn(account)` | Auth/session recovery: validate mapping, fetch cloud setup/categories before defaults, restore pending work, refresh supported records. First authentication/linking needs a connection. A previously authenticated restored UID may read its own Room data offline; signed-out users cannot open financial routes. |
| `saveCategory(account, draft)` / `deleteCategory(account, categoryId)` | Category owner: validate, write/mark local row and queue snapshot atomically; deletion follows the archive proposal below. |
| `saveExpense(account, draft)` / `deleteExpense(account, expenseId)` | Expense owner: validate same-user category, save/mark locally and enqueue together; schedule separate receipt work. |
| `saveMonthlyGoal(account, monthKey, minimumCents, maximumCents)` | Monthly-goal owner: compute inclusive dates, preserve expired-draft checks, update one local month and enqueue together. |
| `importExistingAccount(account)` | Explicitly linked account only: allocate persistent mappings, import categories then expenses and monthly goals; resume the same import after interruption. |
| `refreshFromServer(account)` | Manual refresh and sign-in: fetch authoritative documents including tombstones, map categories before expenses, update clean Room rows, retain dirty/conflicted edits. |
| `scheduleRetry(account)` / `retryPending(account)` | After local changes and session recovery: schedule account-tagged, network-constrained WorkManager work; process durable operations, then refresh. |
| `uploadReceipt(account, expenseId, receiptJobId)` / `downloadReceipt(account, expenseId)` / `retryReceiptCleanup(account)` | Receipt owner: recover transfers/publication/deletion, authenticate Storage requests, populate private viewer files. |
| `resolveConflict(account, mutationId, choice)` | Explicit review: accept server state and cancel that record's superseded queued edits, or submit the reviewed latest local edit against current revision using a new mutation ID. Retain edits until that choice; never revive a tombstone implicitly. |
| `onSignedOut(account)` | Cancel/detach workers, listeners and transfers; invalidate session generation; clear navigation/drafts and viewer state; retain that account's Room rows/queue for its next authenticated session. |

Save the local record, mapping, incremented `localSequence` and pending operation in **one Room transaction**. The local save result means **saved locally / pending**, not synced. Planned queue fields are `mutationId: String`, `userId: Long`, `uid: String`, `recordType: String` (`CATEGORY`, `EXPENSE`, `MONTHLY_GOAL`), `recordId: String`, `operation: String` (`UPSERT`, `DELETE`), typed field snapshot, `localSequence: Long`, `baseRevision: Long?`, `state: String`, `attemptCount: Long = 0`, `lastError: String?`. All are non-null except the last error and a base revision waiting for its preceding operation. Once first sent, snapshot/base revision/ID are immutable. No passwords, tokens or financial descriptions in error logs. Unlinked legacy rows are imported only after D3 linking; they cannot be sent under a guessed UID (Google, 2026j).

1. Send in per-record order. Set the next operation's base revision from the previous acknowledgement before its first send. `0` means a genuinely new document, not a deleted one. Do not coalesce an operation already sent.
2. In a Firestore transaction, first check `users/{uid}/mutations/{mutationId}`. If already present and matching the record/base revision, return its result without rewriting the record. Otherwise read the record, require matching revision (or absence for a new create), reject edits to tombstones, then write the next revision and acknowledgement atomically. The proposed immutable acknowledgement fields are `schemaVersion: Long = 1`, `recordType: String`, `recordId: String`, `baseRevision: Long`, `appliedRevision: Long`, `appliedAt: Timestamp`, all non-null. Retain acknowledgements so an old retry remains safe even after another device changes `lastMutationId`. Transaction callbacks may repeat and must not mutate Room/UI; offline transactions cannot serve as local saves (Google, 2026x).
3. Only a successful server transaction or a matching acknowledgement fetched from the server means **server acknowledged**. Cache snapshots and local pending Firestore writes do not count. Acknowledge only that mutation/local sequence in Room; a newer edit remains pending. Refresh the current cloud record after recovering an older acknowledgement.
4. Downloads never overwrite dirty local snapshots. Compare revisions, retain the server version separately for review, and mark **conflict** if incompatible. For a rejected update, the user chooses server state or a fresh reviewed mutation; no device-clock ordering or silent last-write-wins.
5. Recheck UID, local mapping and session generation before sending, applying a download or acknowledging. Guard Room application inside its transaction. A callback from a session signed out and signed back into the same UID is still stale. Cancellation cannot undo a committed server write; recover its acknowledgement only in that account's new session.

Use queue states `PENDING`, `RETRY`, `WAITING_FOR_SIGN_IN`, `FAILED`, `CONFLICT`, `ACKNOWLEDGED`. Record/receipt UI reports saved locally, pending, synced, failed/retry or conflict needing review. Network/service failures retry with exponential backoff; use WorkManager's network constraint and durable queue, not a loop in a screen. Invalid data, denied rules and missing setup stop automatic retries with a useful next action; expired sessions wait for sign-in. WorkManager scheduling does not guarantee immediate delivery (Google, 2026q; Google, 2026w).

## Linking, import, defaults and deletion

Linking is explicit: prove control of the chosen existing local account through the agreed D3 transition, authenticate the intended Firebase UID, then let the user link that local row. Reject a UID already linked elsewhere locally or a row already linked to another UID. A matching username alone is insufficient. Existing data/files stay under the original local ID. Remove plaintext credential retention through the agreed migration, with no password-query login fallback; test relevant supported upgrade paths and export the new schema. Recover the missing v3 test fixture from evidence rather than assuming migrations were tested (Google, 2026u).

On a fresh second device, authenticate first and create/reuse a local row by UID; do not import a different local account because its username looks similar. Revalidate the session before showing the existing financial navigation. Switching accounts uses a new session generation and local ID; queued work never changes owner. Retained receipts may only be reached through that account's scoped expense reads.

For initial import, persist every local-row/cloud-ID mapping and mutation before network work. Record an account-scoped import checkpoint and per-row acknowledgements; complete categories before dependent expenses, then monthly goals. Receipt transfer is tracked separately and must remain visibly pending. Repeating import must reuse IDs and skip acknowledged rows. If another device already imported these same records and this database has no shared mapping, stop for explicit reconciliation; UUID generation alone cannot deduplicate two independent copies of a legacy database. Never merge categories/expenses just by matching names, amounts or dates.

Read the cloud setup document **and categories including tombstones from the server** before considering defaults. Do not seed from an offline-empty cache. Only seed a new account when the server has no categories, the flag is false and no legacy import needs reconciliation. Initialise Groceries, Transport, Entertainment and Rent with reserved IDs `default-groceries`, `default-transport`, `default-entertainment`, `default-rent`; create their metadata/acknowledgements and set the flag in one transaction. For legacy accounts, import/reconcile first, then set cloud/local flags; deleted local defaults stay deleted. An established empty account stays empty. Reserved defaults are the exception to UUID IDs; never recreate them after the flag is true.

**Proposed category removal: archive every removed category** with `isDeleted = true`, keeping its local/cloud mapping, name and limits. Hide it from new assignments/active progress, but retain it for History and historical totals. This changes the current block-used-category policy and requires D5 agreement. Existing expenses may keep their original archived reference; new assignments to it fail. If removal races an unsent new expense, keep the expense locally and request a replacement active category. Server expense writes read/check the target category in the transaction and rules; downloads must not drop a saved expense because its referenced category is now archived. An edit that retains an expense's existing archived category may change its other fields. Old updates never unarchive a category. Renaming preserves IDs/references.

Expense/monthly-goal deletion also retains a tombstone and pending marker. Active views exclude deleted expenses/goals; totals must exclude deleted expenses while still including active expenses under archived categories. No ordinary cloud physical delete is proposed. All clients must download deletion markers, including after reinstall, so stale operations cannot recreate removed records.

## Receipt transfers and cleanup

Propose object path `users/{uid}/receipts/{expenseCloudId}/{receiptJobId}` where `receiptJobId` is a UUID persisted before upload. A replacement gets a new path; never overwrite a previously published image. Proposed receipt policy: decoded JPEG/PNG only, `0 < size <= 10 MiB`, with matching detected MIME metadata. Validate actual bytes; `.jpg` alone is insufficient. Oversize/unsupported existing photos remain local with an actionable transfer failure. This policy awaits D4; Storage rules validate owner, path, size and content type, while delete rules are separate because a delete has no incoming upload metadata (Google, 2026y).

Proposed local receipt-job fields are `receiptJobId: String`, `userId: Long`, `uid: String`, `expenseCloudId: String`, `sourceLocalPath: String?`, `targetObjectPath: String`, `previousObjectPath: String?`, `publicationMutationId: String?`, `state: String`, `attemptCount: Long = 0`, `lastError: String?`. Cleanup-only jobs need no source file or publication mutation. Publication uses the expense revision/mutation protocol. Proposed phases are `UPLOAD_PENDING`, `UPLOADED`, `PUBLICATION_PENDING`, `PUBLISHED`, `CLEANUP_PENDING`, `DONE` or `FAILED`.

Save the expense immediately with its local receipt. During replacement, the financial write keeps the previous remote reference until the new image is published. For `KEEP`, prepare the remote field from the latest acknowledged path before first send, not a stale form/cache path. Upload only once the cloud expense exists; retry the same path/bytes and retain the job after restart. Publish `receiptObjectPath` only after upload succeeds. Financial acknowledgement does not mean the receipt is synced. A rejected publication keeps the edit/object for review; check server acknowledgement before treating an object as orphaned.

Download through an authenticated Storage reference into an account-scoped private temporary file, validate it, then move it under that account's private receipt directory. Save `receiptImageUri` only if the session and remote path still match. Interrupted downloads leave no usable partial receipt; retry the same remote path. Use authenticated file download rather than a stored download-token URL (Google, 2026r).

Queue cleanup of old remote/local files only after acknowledged replacement, receipt removal or expense deletion. Before deleting, re-read current server expense/reference and any relevant pending job; never delete a currently referenced or unresolved-publication object. New immutable paths must not be reused by later publications. Treat already-missing objects/files as successful cleanup, retain failures, and retry under the captured owner. Reconcile persisted uploaded-but-unpublished jobs on session recovery; no broad bucket wipe or promised automatic server garbage collection. Interrupted upload, publication, replacement and cleanup must each be tested separately.

## Feature-owner handover and acceptance

Names/persons and feature schemas are **unassigned**. Savings and achievements remain blocked for slice 9; the integration work must not invent their entities or complete their UI/calculations.

| Feature | Owner must supply before integration | Planned coordinator boundary |
|---|---|---|
| Savings goals | Actual Room schema/DAO, cloud field names/types/nullability/validation, schema version, stable goal ID, target/current-value meaning, target-date format, notes/names, contribution/edit/delete rules and relation to expenses. Define whether contributions are immutable events or reviewed absolute totals, including how the same contribution is recognised after a retry. UI fields do not settle this. | Reserve `saveSavingsGoal`, `recordSavingsContribution`, `deleteSavingsGoal`; parameter records remain undefined until the owner contract arrives. Each uses captured ownership and atomic local enqueue. |
| Earned achievements | Stable badge identifiers, earning rules/version, earned-date meaning, persisted versus derived fields, streak/day timezone rules, backdated-edit/deletion effects and whether earned badges can be revoked. Fixed display progress cannot become authoritative data. | Reserve `recordEarnedAchievement`; payload/schema and any revocation entry point await the owner. Repeated award calls must refer to the same earned event. |
| Graphs/dashboard/own features | Owner confirms data requirements, refresh after Room downloads and acceptance against both brief descriptions of graphs/progress. Identify the two Part 1 own features and lecturer feedback separately. | Read synced Room expenses, category limits and monthly goals; do not upload independently editable derived spending totals. |

Every scenario below is **planned and not executed**. Record build/commit, devices/accounts, steps, expected/actual result and evidence when the relevant slice exists.

| Scenario | Required result / later evidence |
|---|---|
| Same username/password, fresh second install | Same UID; exact categories/both limits, expenses/all fields and one goal per month appear in Room. Verify non-ASCII names, month-end/leap dates and cent boundaries. |
| Local save, offline restart, repeated import/retry | Saved rows/files/queue survive; reuse IDs; one accepted mutation; no duplicates. Room migration/transaction and queue tests. |
| Concurrent edit, edit during upload, edit versus delete | Explicit conflict recovery; older acknowledgement leaves newer edit pending; tombstones remain deleted. Unit/Room tests plus two-device checks. |
| Defaults/category removal | Returning empty account stays empty; simultaneous setup creates one set; archive preserves historical expenses; pending new expense requests reassignment. |
| Another UID, signed-out client, account switch during work | Database and Storage access denied; no stale response/draft/file enters the new session. Direct emulator rules tests and device checks. |
| Receipt on fresh second install; interruption/replacement/removal | Authenticated viewer works; each transfer phase recovers; referenced files survive cleanup; financial save remains available. Storage tests and devices. |
| Savings contribution retried, concurrent contribution, restart, second device | Owner-defined balance changes exactly once per contribution, with no lost update; date/note/goal survive; expense-derived totals remain consistent. Blocked by owner contract. |
| Badge awarded twice, reload, account switch, backdated expense edit | Same earned badge/event persists without duplication across restart/devices; another account cannot inherit it; owner-defined recalculation/revocation holds. Blocked by owner contract. |
| Physical-phone submission | App, online saving/retrieval, graphs/progress and own features demonstrated with voiceover. Backend/app emulators do not replace the phone requirement. |

## Slice 2 boundary and verification record

Next slice adds only Firebase BoM/main modules, Google services/network configuration, locked owner-scoped rules, reproducible emulator tests and a basic CI workflow. Recheck compatible versions then; do not copy obsolete Firebase `-ktx` modules or upgrade unrelated dependencies (Google, 2026m). The Android client configuration is not a privileged credential; service-account keys/passwords stay out of the app/source/logs. Configuration cannot replace rules (Google, 2026s).

Rules must check `request.auth.uid` against the path owner, exact allowed fields/types, limits, immutable ownership, revisions/tombstones, matching category references and atomic acknowledgement writes. Setup/default rules are separate from record mutations. Deny unimplemented savings/achievement and unmatched paths. Test own-user, other-user, signed-out and malformed writes directly; no production deployment in slice 2 without authorisation (Google, 2026p; Google, 2026z; Google, 2026o).

I verified documentation coverage against the local plan, each existing entity field, current DAOs/navigation/receipt code, teammate feature state and the relevant brief/guide pages. I checked official source titles, links and dates and matched citation labels. This is source/document review, not evidence of cloud behaviour. Compilation, app automated tests, hosted CI and manual device checks were **not run** for this documentation-only slice. `NO-SOURCE` is not a passing test suite, and a workflow file would not prove a successful hosted CI run. D1–D6 remain open; slice 1's reviewed draft is available, but its final decision/acceptance gate is not passed.

Later handover/README must record setup/configuration, linking/import, fields/functions, retry/conflict/delete/receipt behaviour, billing, useful redacted logs, actual build/test/CI results and limitations. Keep the local plan and slice handover outside the repository; GitHub submission, APK/video/assets and lecturer-feedback evidence remain later work.

## References

This is the tracked reference location for this contract and existing source comments. I retained Google 2026a–k, Oracle n.d.-a–c and SQLite labels; new Google sources use 2026l–z rather than the local plan's colliding labels. The list is alphabetised by author and title within the same year, so retained suffixes do not run in list order. URLs/titles and available update metadata were checked on 13 October 2026; Google years follow page updates, Oracle pages have no reliable publication year, and SQLite pages show 2025/2026 updates. These sources support the decisions without claiming historical consultation or copied code. I applied the guide's suffix, website and reference-list rules (The Independent Institute of Education, 2026b, pp.9, 16-17, 45, 51-52).

For slice 2, I rechecked the reused Firebase setup/security/testing sources and added Google 2026aa–af below, retaining all existing labels. Their available page updates are in 2026; access dates record 14 October 2026. This file is the intended shared tracked location, but remains unstaged/untracked until reviewed and added.

Google, 2026a. *Access app-specific files*. [online] Available at: <https://developer.android.com/training/data-storage/app-specific> [Accessed 13 October 2026].

Google, 2026l. *Access data offline*. [online] Available at: <https://firebase.google.com/docs/firestore/manage-data/enable-offline> [Accessed 13 October 2026].

Google, 2026b. *Access data using Room DAOs*. [online] Available at: <https://developer.android.com/training/data-storage/room/accessing-data> [Accessed 13 October 2026].

Google, 2026m. *Add Firebase to your Android project*. [online] Available at: <https://firebase.google.com/docs/android/setup> [Accessed 13 October 2026].

Google, 2026n. *Authenticate with Firebase using password-based accounts on Android*. [online] Available at: <https://firebase.google.com/docs/auth/android/password-auth> [Accessed 13 October 2026].

Google, 2026o. *Build unit tests*. [online] Available at: <https://firebase.google.com/docs/rules/unit-tests> [Accessed 13 October 2026].

Google, 2026aa. *Connect to the network*. [online] Available at: <https://developer.android.com/develop/connectivity/network-ops/connecting> [Accessed 14 October 2026].

Google, 2026ad. *Connect your app to the Cloud Firestore Emulator*. [online] Available at: <https://firebase.google.com/docs/emulator-suite/connect_firestore> [Accessed 14 October 2026].

Google, 2026p. *Control access to specific fields*. [online] Available at: <https://firebase.google.com/docs/firestore/security/rules-fields> [Accessed 13 October 2026].

Google, 2026c. *Date pickers*. [online] Available at: <https://developer.android.com/develop/ui/compose/components/datepickers> [Accessed 13 October 2026].

Google, 2026q. *Define work requests*. [online] Available at: <https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work> [Accessed 13 October 2026].

Google, 2026r. *Download files with Cloud Storage on Android*. [online] Available at: <https://firebase.google.com/docs/storage/android/download-files> [Accessed 13 October 2026].

Google, 2026af. *Firebase Android SDK release notes*. [online] Available at: <https://firebase.google.com/support/release-notes/android> [Accessed 14 October 2026].

Google, 2026s. *Firebase security checklist*. [online] Available at: <https://firebase.google.com/support/guides/security-checklist> [Accessed 13 October 2026].

Google, 2026t. *Get started with Cloud Storage on Android*. [online] Available at: <https://firebase.google.com/docs/storage/android/start> [Accessed 13 October 2026].

Google, 2026ac. *Install, configure and integrate Local Emulator Suite*. [online] Available at: <https://firebase.google.com/docs/emulator-suite/install_and_configure> [Accessed 14 October 2026].

Google, 2026ab. *Learn the core syntax of the Firebase Security Rules for Cloud Storage language*. [online] Available at: <https://firebase.google.com/docs/storage/security/core-syntax> [Accessed 14 October 2026].

Google, 2026d. *Material Design 3 in Compose*. [online] Available at: <https://developer.android.com/develop/ui/compose/designsystems/material3> [Accessed 13 October 2026].

Google, 2026u. *Migrate your Room database*. [online] Available at: <https://developer.android.com/training/data-storage/room/migrating-db-versions> [Accessed 13 October 2026].

Google, 2026e. *Navigation*. [online] Available at: <https://developer.android.com/guide/navigation> [Accessed 13 October 2026].

Google, 2026f. *Photo picker*. [online] Available at: <https://developer.android.com/training/data-storage/shared/photo-picker> [Accessed 13 October 2026].

Google, 2026g. *Save data in a local database using Room*. [online] Available at: <https://developer.android.com/training/data-storage/room> [Accessed 13 October 2026].

Google, 2026ae. *sdkmanager*. [online] Available at: <https://developer.android.com/tools/sdkmanager> [Accessed 14 October 2026].

Google, 2026h. *Side-effects in Compose*. [online] Available at: <https://developer.android.com/develop/ui/compose/side-effects> [Accessed 13 October 2026].

Google, 2026i. *State and Jetpack Compose*. [online] Available at: <https://developer.android.com/develop/ui/compose/state> [Accessed 13 October 2026].

Google, 2026v. *Supported data types*. [online] Available at: <https://firebase.google.com/docs/firestore/manage-data/data-types> [Accessed 13 October 2026].

Google, 2026w. *Task scheduling*. [online] Available at: <https://developer.android.com/develop/background-work/background-tasks/persistent> [Accessed 13 October 2026].

Google, 2026j. *Transaction*. [online] Available at: <https://developer.android.com/reference/androidx/room/Transaction> [Accessed 13 October 2026].

Google, 2026x. *Transactions and batched writes*. [online] Available at: <https://firebase.google.com/docs/firestore/manage-data/transactions> [Accessed 13 October 2026].

Google, 2026y. *Use conditions in Firebase Cloud Storage Security Rules*. [online] Available at: <https://firebase.google.com/docs/storage/security/rules-conditions> [Accessed 13 October 2026].

Google, 2026k. *Write asynchronous DAO queries*. [online] Available at: <https://developer.android.com/training/data-storage/room/async-queries> [Accessed 13 October 2026].

Google, 2026z. *Writing conditions for Cloud Firestore Security Rules*. [online] Available at: <https://firebase.google.com/docs/firestore/security/rules-conditions> [Accessed 13 October 2026].

Oracle, n.d.-a. *BigDecimal (Java SE 17 & JDK 17)*. [online] Available at: <https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html> [Accessed 13 October 2026].

Oracle, n.d.-b. *Calendar (Java SE 17 & JDK 17)*. [online] Available at: <https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Calendar.html> [Accessed 13 October 2026].

Oracle, n.d.-c. *SimpleDateFormat (Java SE 17 & JDK 17)*. [online] Available at: <https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/text/SimpleDateFormat.html> [Accessed 13 October 2026].

SQLite, 2025. *Built-in aggregate functions*. [online] Available at: <https://www.sqlite.org/lang_aggfunc.html> [Accessed 13 October 2026].

SQLite, 2026. *SELECT*. [online] Available at: <https://www.sqlite.org/lang_select.html> [Accessed 13 October 2026].

The Independent Institute of Education, 2026a. *OPSC7311/PROG7313 portfolio of evidence: Paper and marking rubrics*. [pdf] [s.l.]: The Independent Institute of Education. Supplied local assessment: `C:/Users/xande/OneDrive - ADvTECH Ltd/2026/Sem 2/OPSC7311/POE/OPSC7311POE.pdf` [Accessed 13 October 2026].

The Independent Institute of Education, 2026b. *The IIE Harvard Anglia style reference guide: Adapted for The IIE 2026*. [pdf] [s.l.]: The Independent Institute of Education. Supplied local guide: `C:/Users/xande/Downloads/Throw Away/The+IIE+Harvard+Anglia+Style+Reference+Guide+-+Adapted+for+The+IIE+2026.pdf` [Accessed 13 October 2026].
