import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';

if (process.env.GCLOUD_PROJECT !== 'demo-pocket-quest'
    || process.env.FIREBASE_AUTH_EMULATOR_HOST !== '127.0.0.1:9099') {
  throw new Error('Account tests require the local demo Auth emulator.');
}

const serial = process.env.ANDROID_SERIAL;
if (!/^emulator-\d+$/.test(serial ?? '')) throw new Error('Set ANDROID_SERIAL to the dedicated PocketQuest_Account_Tests emulator.');
const propertiesFile = new URL('../local.properties', import.meta.url);
const properties = existsSync(propertiesFile) ? readFileSync(propertiesFile, 'utf8') : '';
const localSdk = properties.match(/^sdk\.dir=(.+)$/m)?.[1].trim().replace(/\\([:\\])/g, '$1');
const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT ?? localSdk;
if (!sdk) throw new Error('Set ANDROID_HOME to the Android SDK.');
const adb = join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
function adbText(args) {
  const result = spawnSync(adb, args, { encoding: 'utf8' });
  if (result.status !== 0) throw new Error('Could not inspect the dedicated Android test emulator.');
  return result.stdout;
}
const devices = adbText(['devices']).split(/\r?\n/).filter(line => /\tdevice\s*$/.test(line)).map(line => line.split('\t')[0]);
if (devices.length !== 1 || devices[0] !== serial) throw new Error('Leave only the dedicated test emulator connected; Gradle can target every connected device.');
const avd = adbText(['-s', serial, 'emu', 'avd', 'name']).split(/\r?\n/)[0].trim();
if (!avd.startsWith('PocketQuest_Account_Tests')) throw new Error('Refused a personal/presentation emulator. Use an AVD named PocketQuest_Account_Tests.');

// I kept account tests on a named demo Firebase app; the selected live client is never used.
// I left APKs installed too, so the runner cannot clear a previously installed test database.
const args = [':app:connectedDebugAndroidTest', `-Pandroid.injected.device.serial=${serial}`,
  '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true', '--console=plain'];
if (process.env.POCKET_CATEGORY_TESTS === '1') {
  if (process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:8080') throw new Error('Category tests require Firestore.');
  args.push('-Pandroid.testInstrumentationRunnerArguments.class=com.example.pocketquestbudgeting.data.CategoryFirebaseTest,com.example.pocketquestbudgeting.data.CategorySyncTest,com.example.pocketquestbudgeting.data.CategoryMigrationTest');
}
if (process.env.POCKET_EXPENSE_TESTS === '1') {
  if (process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:8080') throw new Error('Expense tests require Firestore.');
  args.push('-Pandroid.testInstrumentationRunnerArguments.class=com.example.pocketquestbudgeting.data.ExpenseFirebaseTest,com.example.pocketquestbudgeting.data.ExpenseSyncTest,com.example.pocketquestbudgeting.data.ExpenseMigrationTest');
}
if (process.env.POCKET_GOAL_TESTS === '1') {
  if (process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:8080') throw new Error('Goal tests require Firestore.');
  args.push('-Pandroid.testInstrumentationRunnerArguments.class=com.example.pocketquestbudgeting.data.MonthlyGoalFirebaseTest,com.example.pocketquestbudgeting.data.MonthlyGoalSyncTest,com.example.pocketquestbudgeting.data.MonthlyGoalMigrationTest');
}
if (process.env.POCKET_SAVINGS_TESTS === '1') {
  if (serial !== 'emulator-5554' || avd !== 'PocketQuest_Account_Tests_API35') throw new Error('Savings tests require the exact dedicated AVD on emulator-5554.');
  if (process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:8080') throw new Error('Savings tests require Firestore.');
  args.push('-Pandroid.testInstrumentationRunnerArguments.class=com.example.pocketquestbudgeting.data.SavingsFirebaseTest,com.example.pocketquestbudgeting.data.SavingsPersistenceTest,com.example.pocketquestbudgeting.data.EarnedAchievementPersistenceTest,com.example.pocketquestbudgeting.data.SavingsMigrationTest');
}
if (process.env.POCKET_RECEIPT_TESTS === '1') {
  if (process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:8080'
      || process.env.FIREBASE_STORAGE_EMULATOR_HOST !== '127.0.0.1:9199') throw new Error('Receipt tests require local Firestore and Storage.');
  args.push('-Pandroid.testInstrumentationRunnerArguments.class=com.example.pocketquestbudgeting.data.ReceiptFirebaseTest,com.example.pocketquestbudgeting.data.ReceiptSyncTest,com.example.pocketquestbudgeting.data.ReceiptMigrationTest');
}
if (process.env.FIREBASE_STORAGE_EMULATOR_HOST !== '127.0.0.1:9199') {
  const excluded = ['ReceiptFirebaseTest'];
  if (process.env.POCKET_SAVINGS_TESTS !== '1') excluded.push('SavingsFirebaseTest');
  args.push(`-Pandroid.testInstrumentationRunnerArguments.notClass=${excluded.map(name => `com.example.pocketquestbudgeting.data.${name}`).join(',')}`);
} else if (process.env.POCKET_SAVINGS_TESTS !== '1') {
  args.push('-Pandroid.testInstrumentationRunnerArguments.notClass=com.example.pocketquestbudgeting.data.SavingsFirebaseTest');
}
// Firestore integration cases need both local emulators; the older Auth-only command excludes them.
if (process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:8080') {
  args.push('-Pandroid.testInstrumentationRunnerArguments.notClass=com.example.pocketquestbudgeting.data.FirebaseSyncTest,com.example.pocketquestbudgeting.data.CategoryFirebaseTest,com.example.pocketquestbudgeting.data.ExpenseFirebaseTest,com.example.pocketquestbudgeting.data.MonthlyGoalFirebaseTest,com.example.pocketquestbudgeting.data.ReceiptFirebaseTest,com.example.pocketquestbudgeting.data.SavingsFirebaseTest');
}
const result = process.platform === 'win32'
  ? spawnSync('cmd.exe', ['/d', '/c', 'gradlew.bat', ...args], { stdio: 'inherit' })
  : spawnSync('./gradlew', args, { stdio: 'inherit' });
if (result.error) throw result.error;
process.exit(result.status ?? 1);
