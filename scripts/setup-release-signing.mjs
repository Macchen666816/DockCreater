import { mkdirSync, existsSync, writeFileSync, chmodSync } from 'node:fs';
import { homedir } from 'node:os';
import { join } from 'node:path';
import { randomBytes } from 'node:crypto';
import { spawnSync } from 'node:child_process';

// Keep signing credentials outside the checkout; never print passwords.
const directory = join(homedir(), '.android', 'dockcreater-release');
const properties = join(directory, 'signing.properties');
const keystore = join(directory, 'dockcreater-release.p12');
if (existsSync(properties) && existsSync(keystore)) {
  console.log('Release signing already exists; reusing it.');
  process.exit(0);
}
if (existsSync(properties) || existsSync(keystore)) {
  throw new Error('Incomplete signing setup exists. Restore the matching files before proceeding.');
}
mkdirSync(directory, { recursive: true, mode: 0o700 });
chmodSync(directory, 0o700);
const password = randomBytes(32).toString('hex');
const javaHome = process.env.JAVA_HOME || '/Applications/Android Studio.app/Contents/jbr/Contents/Home';
const result = spawnSync(join(javaHome, 'bin', 'keytool'), [
  '-genkeypair', '-noprompt', '-storetype', 'PKCS12', '-keystore', keystore,
  '-alias', 'dockcreater', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
  '-dname', 'CN=Dock Studio, OU=Personal Apps', '-storepass:env', 'DOCK_SIGNING_PASSWORD',
  '-keypass:env', 'DOCK_SIGNING_PASSWORD',
], { env: { ...process.env, DOCK_SIGNING_PASSWORD: password }, encoding: 'utf8' });
if (result.status !== 0) throw new Error(`Key generation failed: ${result.stderr}`);
chmodSync(keystore, 0o600);
writeFileSync(properties, `storeFile=${keystore}\nstorePassword=${password}\nkeyAlias=dockcreater\nkeyPassword=${password}\n`,
  { flag: 'wx', mode: 0o600 });
console.log(`Release signing created in ${directory}. Back up both files securely.`);
