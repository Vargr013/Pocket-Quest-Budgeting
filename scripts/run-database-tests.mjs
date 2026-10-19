import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const serial = process.env.ANDROID_SERIAL;
if (serial !== 'emulator-5554') throw new Error('Set ANDROID_SERIAL=emulator-5554 for the dedicated database test AVD.');
const properties = readFileSync(new URL('../local.properties', import.meta.url), 'utf8');
const localSdk = properties.match(/^sdk\.dir=(.+)$/m)?.[1].trim().replace(/\\([:\\])/g, '$1');
const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT ?? localSdk;
if (!sdk) throw new Error('Set ANDROID_HOME to the Android SDK.');
const adb = join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
function inspect(args) {
  const result = spawnSync(adb, args, { encoding: 'utf8' });
  if (result.status !== 0) throw new Error('Could not inspect the dedicated test emulator.');
  return result.stdout;
}
const devices = inspect(['devices']).split(/\r?\n/).filter(line => /\tdevice\s*$/.test(line)).map(line => line.split('\t')[0]);
if (devices.length !== 1 || devices[0] !== serial) throw new Error('Leave only the dedicated test emulator connected.');
const avd = inspect(['-s', serial, 'emu', 'avd', 'name']).split(/\r?\n/)[0].trim();
if (avd !== 'PocketQuest_Account_Tests_API35') throw new Error('Refused a personal/presentation AVD.');
if (inspect(['-s', serial, 'shell', 'getprop', 'sys.boot_completed']).trim() !== '1') throw new Error('Wait for the test AVD to boot.');

// I selected only local database fixtures; these tests do not call Firebase or open the app database.
const classes = ['SavingsPersistenceTest', 'EarnedAchievementPersistenceTest', 'SavingsMigrationTest',
  'AccountMigrationTest', 'SyncMigrationTest', 'CategoryMigrationTest', 'ExpenseMigrationTest',
  'MonthlyGoalMigrationTest', 'ReceiptMigrationTest', 'AccountSessionTest', 'SyncFoundationTest',
  'CategorySyncTest', 'ExpenseSyncTest', 'MonthlyGoalSyncTest', 'ReceiptSyncTest'];
const args = [':app:connectedDebugAndroidTest', `-Pandroid.injected.device.serial=${serial}`,
  '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true',
  `-Pandroid.testInstrumentationRunnerArguments.class=${classes.map(name => `com.example.pocketquestbudgeting.data.${name}`).join(',')}`,
  '--console=plain'];
const result = process.platform === 'win32'
  ? spawnSync('cmd.exe', ['/d', '/c', 'gradlew.bat', ...args], { stdio: 'inherit' })
  : spawnSync('./gradlew', args, { stdio: 'inherit' });
if (result.error) throw result.error;
process.exit(result.status ?? 1);
