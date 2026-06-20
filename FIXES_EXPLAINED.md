# Fixes explained

This covers every change made in this pass, why it was needed, why the fix
was built the way it was (including options considered and rejected), and
what consequences were traced through the rest of the codebase before
considering each one done. It also calls out two things found and fixed
along the way that weren't part of the original ask, because they were
blocking the actual fix from working.

---

## 1. Row-level security was a complete no-op — now it actually enforces

### What was wrong

`V15__enforce_rls.sql` enabled RLS on `vector_collections` and
`vector_documents` with a policy like:

```sql
USING (
    current_setting('app.tenant_id', true) IS NULL
    OR user_id = current_setting('app.tenant_id', true)::uuid
)
```

Two independent things made this provide zero actual protection:

1. **Nothing in the Java codebase ever set `app.tenant_id`.** Grepped every
   `.java` file for `SET LOCAL`, `current_setting`, `app.tenant_id` — zero
   matches outside this migration's own SQL. So
   `current_setting('app.tenant_id', true)` was always NULL, and the
   `OR ... IS NULL` clause meant: always grant access to everything.

2. **Even if the GUC had been set correctly, this app connects to Postgres
   using a single configured role** (`SPRING_DATASOURCE_USERNAME`) that is
   also the role that runs the Flyway migrations creating these tables —
   i.e. it's the table owner. PostgreSQL exempts table owners from RLS
   policies by default unless `FORCE ROW LEVEL SECURITY` is also set. This
   migration never set it. Confirmed against the official Postgres docs and
   multiple independent sources before treating this as fact, not just
   inferring it.

So this wasn't "leaky under an edge case" — it provided literally the same
protection as not having RLS at all, while reading in a migration log like
tenant isolation had been hardened at the database layer. That gap between
what it looked like it did and what it actually did is worse than just
not having the feature.

### What changed and why

**`TenantAwareDataSourceWrapper.java` + `TenantAwareDataSourceBeanPostProcessor.java`**
(new files, `cloudpool-common`) — these make the GUC actually get set,
reliably, on every transactional connection.

Why a DataSource wrapper specifically, and not one of the other options
considered:

- **A Hibernate `Interceptor`/`StatementInspector`** would work but runs
  per-statement, which is wasteful (re-setting the same GUC before every
  single query in a transaction) and ties the fix to Hibernate internals
  specifically, rather than working for any consumer of the `DataSource`
  bean (JdbcTemplate, shedlock's lock provider, etc.).
- **An `@Aspect` around `@Transactional` methods** would need a new
  `spring-boot-starter-aop` dependency added to every module, increasing
  blast radius for something that doesn't need it.
- **A `BeanPostProcessor` wrapping the `DataSource` bean itself** needs no
  new dependencies (every module already has the classes this uses), and
  every consumer of that bean gets the behavior automatically via normal
  Spring dependency injection — no per-consumer changes needed.

The wrapper only acts when `TransactionSynchronizationManager.isActualTransactionActive()`
is true. This matters because Postgres raises `SET LOCAL can only be used
in transaction blocks` outside a transaction, and plenty of connections in
this app are checked out outside any application transaction — health
checks, Flyway's own migration connection, schema validation at boot.
Verified (via search, since I wasn't fully certain of the exact internal
ordering) that Spring's `JpaTransactionManager` actually starts the
transaction synchronization *before* the physical JDBC connection is
acquired, which is what makes checking this flag inside `getConnection()`
correct rather than a race.

It uses `SET LOCAL` (via the parameterized `set_config(name, value, true)`
— the third argument is what makes it `LOCAL`-scoped) rather than plain
`SET`, specifically because of connection pooling: HikariCP reuses the same
physical connection across many different tenants' requests over time.
`SET` without `LOCAL` would persist past the end of the transaction and
leak into whoever's request reuses that pooled connection next — which
would be a *new*, different cross-tenant bug, arguably worse than the
original no-op because it would apply the *wrong* tenant's context instead
of no context at all.

It only fires against real PostgreSQL (checked via
`connection.getMetaData().getDatabaseProductName()`), not H2. This matters
because this app's local/dev setup runs H2 in `MODE=PostgreSQL`
*compatibility* mode by default (see `cloudpool-data/application.yml`),
and H2's Postgres compatibility mode does not implement `ROW LEVEL
SECURITY` or `current_setting()` — those are real Postgres engine
features, not something a SQL-syntax compatibility mode can emulate. This
is also why the migrations themselves had to move (next section).

**`V15__enforce_rls.sql`** — rewritten to add `FORCE ROW LEVEL SECURITY`
on both tables, and to remove the `OR ... IS NULL` fallback entirely. Once
the wrapper above reliably sets the GUC for every real authenticated
request, there's no legitimate reason for the policy to have a "shrug and
allow everything" escape hatch — that fallback only existed to paper over
the GUC not being set, which is now actually fixed at the source. A
database-layer security control with a built-in bypass for "the
application forgot to do its part" isn't defense in depth, it's the
appearance of defense in depth.

**Consequence I checked before removing the fallback:** with no
`IS NULL` escape, any code path that legitimately needs cross-tenant or
no-tenant access to these tables would now get zero rows instead of
everything. Traced every caller of `VectorCollectionRepository` /
`VectorDocumentRepository` (`VectorService.java`, `GraphQLController.java`)
— every single method is `@Transactional`, called synchronously from a
request thread that already went through `TenantFilter` (no `@Async`,
no `@Scheduled` job touches these tables). So there's no legitimate
existing path that would break. If a future admin/analytics feature needs
to query across all tenants, the right tool is a dedicated database role
with the `BYPASSRLS` attribute — documented inline in the migration — not
relying on this policy quietly stepping aside.

---

## 2. `V16` used invalid Postgres syntax and would have broken the migration chain

### What was wrong

```sql
ALTER ROLE CURRENT_USER SET statement_timeout = '30s';
```

`ALTER ROLE`'s name parameter must be a literal role identifier.
`CURRENT_USER` is a function/keyword evaluated at query time, not
something Postgres will substitute when parsing this specific statement
form. This would fail at apply time (something like `role "current_user"
does not exist`), which breaks the entire Flyway migration chain for any
fresh deployment against real Postgres — every migration after it would
never run either, since Flyway applies them in strict order and stops on
the first failure.

### What changed and why

Couldn't just hardcode the real role name, because it's only known via
`${SPRING_DATASOURCE_USERNAME}` at deploy time, not as a literal a
migration file can reference. Same problem exists for hardcoding a
database name. The fix applies the timeout at the database level instead
(affects every role connecting to this database — arguably what you
actually want here, a blanket safety net, not a per-role one), resolved
dynamically via `current_database()` inside a `DO` block, since
`ALTER DATABASE` also requires a literal name and can't take a function
call as that argument directly:

```sql
DO $$
BEGIN
    EXECUTE format(
        'ALTER DATABASE %I SET statement_timeout = %L',
        current_database(),
        '30s'
    );
END
$$;
```

Verified `%I` (identifier-quote) and `%L` (literal-quote) are the correct,
not-swapped specifiers for `format()` against the official Postgres
documentation before relying on them — getting those backwards would have
been its own new bug.

Like V15, this only runs against real Postgres now (see next section) — H2
doesn't have `statement_timeout`-as-a-role/database-setting the same way
either, so this migration would have had the same dev-environment problem
V15 did even once the syntax was fixed.

---

## 3. Both migrations moved to a Postgres-only Flyway location

### What was wrong

Flyway, by default, applies every `.sql` file under `classpath:db/migration`
against whatever datasource is active — H2 in local/dev (`MODE=PostgreSQL`
*compatibility* mode, not real Postgres), real Postgres in production. V15
and V16 both use genuine Postgres engine features (`ROW LEVEL SECURITY`,
`current_setting`, `ALTER DATABASE ... SET`) that H2's compatibility mode
doesn't implement. That means, independent of the syntax/logic bugs above,
**these migrations would already break a default local/dev boot today**
just from being placed in the shared migration path.

### What changed and why

Flyway supports vendor-specific migration locations: configuring
`spring.flyway.locations: classpath:db/migration,classpath:db/migration/{vendor}`
makes Flyway substitute the actual JDBC vendor name (`h2` or `postgresql`)
automatically and only pick up migrations from the matching subfolder. V15
and V16 moved into `db/migration/postgresql/`; the vendor-neutral ones
(V1–V14, V17 — confirmed V17's `CREATE INDEX IF NOT EXISTS` syntax works on
both engines) stayed in the shared path.

Verified Flyway tolerates the resulting version-number gap (V14 → V17 on
H2, since V15/V16 simply don't exist from H2's point of view) — this is
normal, expected Flyway behavior; it only objects to *out-of-order
application* relative to already-applied state, not to gaps in the version
sequence itself.

---

## 4. Five Spring Boot services had a YAML bug that silently dropped their configured port

### What this is

Found while reading `cloudpool-data/application.yml` to design the RLS
fix, not something I was originally looking for. Every one of the five
Spring Boot services (`auth`, `network`, `compute`, `data`, `gateway`) had
**two separate top-level `server:` keys** in the same file, e.g.:

```yaml
server:
  port: 8083

server:
  forward-headers-strategy: FRAMEWORK
```

YAML doesn't merge duplicate top-level mapping keys — the second
occurrence silently replaces the first entirely when parsed. That means
`forward-headers-strategy: FRAMEWORK` was the only setting that actually
took effect; `port: 8083` (and in `cloudpool-data`'s case, also
`servlet.context-path` and `servlet.max-http-post-size`) was silently
discarded, and Spring Boot would fall back to its default port `8080` for
every one of these services — almost certainly not what was intended given
each service has a distinct, deliberately-assigned port (8082, 8083, 8084,
8085, 8080-for-gateway).

`cloudpool-data` additionally had a `spring.server.port` entry (nested
under the top-level `spring:` key) which isn't a real Spring Boot property
path at all — `server.*` properties must be top-level, not nested under
`spring:` — so that was a second, independent reason that particular
`port: 8083` was never actually being applied.

### What changed

Merged each pair into a single top-level `server:` block per service,
preserving every distinct setting that existed across both halves (port,
servlet config where present, forward-headers-strategy). This is purely a
correctness fix for an existing, already-broken behavior — not a new
feature or a behavior change beyond "the port you can see configured in
the file is the port that's actually used."

---

## 5. Gateway's OAuth2/x509 config would very likely have prevented the gateway from booting at all

### What was wrong

```java
.oauth2ResourceServer(ServerHttpSecurity.OAuth2ResourceServerSpec::jwt)
.x509(x509 -> x509.principalExtractor(cert -> cert.getSubjectDN().getName()));
```

with comments claiming "strict JWT signature validation" and "mTLS for
internal microservice communication." Neither claim matched the actual
infrastructure:

- `oauth2ResourceServer().jwt()` validates asymmetric tokens fetched from a
  JWKS endpoint (`jwk-set-uri`/`issuer-uri`) or via an explicit
  `JwtDecoder` bean. Checked every `.yml`/`.properties` file in the repo —
  neither property exists anywhere, and there's no `JwtDecoder` bean
  defined. The actual JWTs this app issues (`JwtUtils.java`, used by every
  backend service's `JwtAuthenticationFilter`) are signed with **HS512
  using a single shared symmetric secret** — a fundamentally different
  scheme this resource-server config has no way to validate against. With
  the DSL method actually invoked and no decoder resolvable, Spring Boot's
  autoconfiguration was very likely to fail the gateway's startup outright.
- `.x509(...)` configures mutual TLS client-certificate auth. Checked for
  any keystore/truststore/CA configuration anywhere in the repo — none
  exists. Enabling this without that infrastructure doesn't add mTLS, it's
  inert at best and request-breaking at worst depending on connector
  config.

### What changed and why

Removed both. The honest, lower-risk fix here was **not** to bolt on a
second, different, hastily-built JWT validator at the gateway layer under
the same kind of time pressure that produced the original broken config —
that's exactly how you get a new subtle bug (blocking calls on a reactive
event loop, secret-loading races, etc.) in a security-critical path.

The real authentication enforcement in this application already happens
correctly, downstream, in every Spring Boot service via
`JwtAuthenticationFilter` validating the actual HS512 tokens. Removing the
broken gateway-level config doesn't weaken that — it removes a layer that
was never functioning, while leaving the working one completely untouched.

**Consequence that had to be handled, not just the removal itself:** the
filter chain also had `.anyExchange().authenticated()` for everything
except `/api/auth/**` and `/public/**`. With no authentication mechanism
configured at the gateway layer at all, there is no way to ever satisfy
`.authenticated()` — every request to every real endpoint would have been
unconditionally rejected at the gateway, never reaching the services that
actually validate tokens correctly. Changed this to `.anyExchange().permitAll()`
with an explanatory comment, since authorization is, and remains, the
downstream services' job.

Left a documented `TODO(security)` for the real longer-term fix: a
dedicated, WebFlux-compatible (non-blocking) JWT validation filter at the
gateway sharing the same HS512 secret, so invalid tokens get rejected at
the edge instead of after a network hop. That's a real feature with its
own design and test surface — deliberately not rushed into this pass.

---

## 6. WAF filter tightened to reduce false positives; body-inspection gap documented, not patched over

### What was wrong

Two real problems, of different severity:

1. The SQLi pattern (`select\s+.*\s+from`, `update\s+.*\s+set`, etc.)
   matched those word sequences **anywhere**, including inside ordinary
   values that happen to contain them — a project field literally named
   something like "select_from_date", for instance — causing legitimate
   requests to get hard-blocked with a 403.
2. Every header was scanned, including `Authorization`. Bearer tokens are
   base64url-encoded and never interpreted as HTML or SQL by anything
   downstream, so scanning them is pure false-positive risk for zero
   protective value.
3. (Pre-existing limitation, not something I introduced or fully fixed —
   see below) The filter never inspects the request body at all, which is
   how virtually every mutation endpoint in this app actually carries
   data.

### What changed and why

Tightened the SQLi pattern to require shapes that actually look like SQL
syntax (`union select`, `' OR '1'='1`, a trailing SQL comment marker, a
stacked `; DROP`/`; DELETE`) rather than just two common English words
appearing near each other. Added an explicit `SKIPPED_HEADERS` set
(`authorization`, `cookie`, `x-api-key`, `x-xsrf-token`) for headers that
structurally can't be interpreted as HTML/SQL and shouldn't be scanned.

**Deliberately did not implement body inspection in this pass.** Spring
Cloud Gateway is fully reactive — the request body is a
`Flux<DataBuffer>`, not something you read synchronously — and this
application accepts uploads up to 5GB (`cloudpool.storage.max-file-size`).
Naively buffering a body to regex-scan it would risk OOM-ing the gateway
on large legitimate uploads, which would be a worse outage than the thing
the filter is trying to prevent. Doing this correctly needs streaming-safe,
size-capped buffering with a content-type allowlist (skip binary uploads
entirely; only scan e.g. `application/json` bodies under some reasonable
size cap) — that's a real, separate feature with its own design and test
surface, the same judgment call as the gateway JWT issue above. Documented
this explicitly in the class-level Javadoc, along with what the filter
*can* honestly be relied on for and what the actual load-bearing defenses
in this app are (parameterized queries everywhere SQL is built; no
server-side unescaped HTML rendering of user content) — so nobody reads
"WAF filter exists" as "request bodies are being inspected for attacks."

---

## 7. Project secrets were "encrypted" with Base64 — now real AES-GCM via the existing EncryptionUtil

### What was wrong

```java
// Simple obfuscation/encryption using Base64 for the DB
String encryptedValue = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
```

in `ProjectService.addSecret()`, with the mirror Base64-decode in
`listSecrets()`. Base64 is an encoding, not encryption — fully reversible
by anyone with the stored value and zero key material. Every project
secret (API keys, DB credentials users store per-project) was effectively
plaintext at rest, sitting in the same codebase where `EncryptionUtil`
(real AES-GCM envelope encryption with HKDF key derivation) already exists
and is already used correctly for `User.customClientSecret` and OAuth
tokens.

### What changed and why

Swapped both methods to call the existing `EncryptionUtil.encrypt()` /
`.decrypt()` (envelope-encryption, `byte[]`-based), Base64-encoding only
the final ciphertext bytes for storage in a `String` column — Base64 is
appropriate *after* encryption (it's just a binary-to-text transport
encoding at that point), the problem was only ever using it *as* the
encryption step.

**Consequence traced and found to need no separate fix:**
`createSnapshot()`/`restoreSnapshot()` read `ProjectSecret.secretValue`
and write it verbatim into/from a JSON snapshot blob — they never
decrypt/re-encrypt it themselves, just copy the stored value through. Once
`addSecret` stores real ciphertext instead of Base64, the snapshot
mechanism is automatically correct too, with zero changes needed there.

**A real, separate, previously-broken bug found and fixed as a
prerequisite:** `EncryptionUtil`'s constructor requires a
`cloudpool.encryption.salt` property (`@Value("${cloudpool.encryption.salt}")`,
no default). Checked every service's `application.yml` — **none of the
four configured it**. Since `EncryptionUtil` is an eagerly-instantiated
`@Component`, this meant **every one of the four Spring Boot services
would fail to start at all**, independent of anything in `ProjectService`
— this had nothing to do with my fix originally, but my fix depends on
this bean working, so it had to be fixed first. Added
`cloudpool.encryption.salt: ${CLOUDPOOL_ENCRYPTION_SALT}` to all four
services, mirroring the existing `master-key` env-var pattern exactly.

**Why this had to be the *same* value across all four services, not a
generated default per service:** they share one database, and any service
might read a column another service encrypted, e.g. `DatabaseConnection`
passwords or `ProjectSecret` values. A different salt per service would
mean a value encrypted by one service silently fails to decrypt when read
by another. Documented this explicitly in each `application.yml` so a
future deploy doesn't accidentally generate four different values.

**Also fixed:** the test config for `cloudpool-auth`'s `TenantIsolationTest`
needed the same salt property added (it boots a real Spring context that
pulls in `EncryptionUtil` transitively via `cloudpool-common`), otherwise
that test — which is the actual regression test for the earlier
`X-Tenant-ID` IDOR fix — would fail to boot for an unrelated reason.

**Known, explicitly out-of-scope consequence:** any project secrets
already stored under the old Base64 scheme will fail to decrypt under the
new `EncryptionUtil.decrypt()` call (Base64-decoded plaintext bytes aren't
valid AES-GCM envelope ciphertext), and will surface as
`[DECRYPTION_ERROR]` in `listSecrets()` rather than the original value.
Did not attempt an automatic dual-format migration (try new scheme, fall
back to old Base64 on failure) — that adds real complexity and its own bug
surface, and this codebase doesn't have any existing migration tooling for
this kind of data transform. Given everything else found across this
review (the app appears to be pre-production-data scale), the responsible
choice was to document this plainly as a deployment note rather than
quietly build a fallback path whose main effect would be making it harder
to ever fully retire the insecure Base64 format. **If there is real
existing secret data, it needs a one-time decrypt-as-Base64 /
re-encrypt-as-AES-GCM migration script before this deploys** — that's a
deliberate, visible follow-up, not something to bury inside `listSecrets()`.

Also removed `ProjectService.cleanChar()`, a 36-branch switch statement
that mapped every letter/digit to itself — confirmed (by checking every
character the caller's guard could ever pass to it) it was a pure identity
function for all reachable inputs, with an unreachable `default: return
'_'` branch. Not a bug, just dead code masquerading as sanitization logic;
removed for clarity since I was already in this method.

---

## What was deliberately left alone

A few things came up during this pass that are real but out of scope for
"fix without creating new issues," and rushing them would have been
exactly the kind of thing this instruction was meant to prevent:

- **A reactive, gateway-level JWT validator** (see section 5's TODO). Real
  feature, needs its own design for non-blocking secret access and token
  parsing on the WebFlux event loop.
- **Streaming-safe WAF body inspection** (see section 6). Needs a
  content-type allowlist and size-capped buffering design before it's safe
  to add to a gateway that accepts multi-gigabyte uploads.
- **A migration script for pre-existing Base64-"encrypted" secrets** (see
  section 7). Needs to know whether any real secret data actually exists
  in a deployed environment before it's worth building.
- **No `.env.example` file exists anywhere in this repo** documenting
  required environment variables (`CLOUDPOOL_ENCRYPTION_MASTER_KEY`,
  `CLOUDPOOL_ENCRYPTION_SALT`, `SPRING_DATASOURCE_*`, etc.) for new
  deployments/contributors. Noticed this while tracing the salt-property
  bug; didn't create one unprompted since it's a meaningfully separate
  piece of work from the fixes asked for here.
