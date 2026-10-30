// Checks that hosted-update.sql applies in one go (as the Supabase SQL Editor runs it) on a
// database that has only the first three migrations, i.e. the state of a project set up with
// hosted-setup.sql. Run: node hosted-update-check.mjs
import { PGlite } from '@electric-sql/pglite';
import { readdirSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = join(here, '..', '..', '..');
const migrationsDir = join(here, '..', '..', 'migrations');

const db = new PGlite();
await db.exec(readFileSync(join(here, 'supabase_stubs.sql'), 'utf8'));
const firstThree = readdirSync(migrationsDir).filter((f) => f.endsWith('.sql')).sort().slice(0, 3);
for (const file of firstThree) await db.exec(readFileSync(join(migrationsDir, file), 'utf8'));

try {
  await db.exec(readFileSync(join(root, 'hosted-update.sql'), 'utf8'));
  const { rows } = await db.query(`
    select (select count(*)::int from pg_proc where proname in
              ('suggest_friends', 'friends_sharing_presence', 'delete_my_account', 'notify_privacy_change')) as functions,
           (select count(*)::int from information_schema.columns
              where table_name = 'daily_activity_totals' and column_name = 'counted_reels') as counted_column`);
  const ok = rows[0].functions === 4 && rows[0].counted_column === 1;
  console.log(ok ? 'hosted-update.sql applies cleanly' : `incomplete: ${JSON.stringify(rows[0])}`);
  process.exit(ok ? 0 : 1);
} catch (e) {
  console.log(`hosted-update.sql failed: ${e.message}`);
  process.exit(1);
}
