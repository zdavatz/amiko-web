# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AmiKoWeb is a Swiss pharmaceutical reference (drug compendium) web application built with Play Framework 2.9.2 (Java). It provides drug information search, drug interaction checking, e-prescriptions, and price comparisons. Supports German and French via multi-tenant hostname detection (amiko.* → German, comed.* → French).

## Build & Run Commands

```bash
# Build distribution ZIP
./bin/activator dist

# Run dev server (port 9000)
./bin/activator run

# Run tests
./bin/activator test

# Run with feature flags disabled
export JAVA_OPTS="-Dfeature.interactions=false"
./bin/activator run

# Enter SBT shell
./bin/activator
```

The build produces `target/universal/amikoweb-1.0-SNAPSHOT.zip`.

## Architecture

**Backend**: Play Framework 2.9.2, Java, Scala 2.13.13, SBT 1.9.6
**Frontend**: TypeScript, CoffeeScript, jQuery, SystemJS
**Database**: SQLite via JDBC (no ORM — raw SQL queries throughout)

### Controllers

- **MainController.java** (~1200 lines) — Core logic: medication search (by name, ATC, regnr, owner, therapy), fachinfo display, full-text search, drug interactions, price comparison, prescription handling
- **OAuthController.java** — HIN OAuth 2.0 and ADSwiss integration for healthcare authentication and e-prescriptions. Credentials are placeholder values replaced by GitHub Actions CI at build time.
- **SwiyuLoginController.java** — swiyu Wallet login via OID4VP (OpenID for Verifiable Presentations), verifier API at swiyu.ywesee.com

### Database Setup

Five SQLite databases injected via `@NamedDatabase`:
- `german` / `french` — Main drug databases (`amiko_db_full_idx_{de,fr}.db`)
- `frequency_de` / `frequency_fr` — Full-text search frequency databases
- `interactions` — SDIF drug interactions database (`interactions.db`)

Database files live in `sqlite/` directory. Tables: `amikodb` (medications), `frequency` (full-text entries). Queries use SQLite GLOB patterns with accent normalization. Large registration number queries are batched in groups of 50.

The `interactions.db` contains 6 tables:
- `drugs` — Drug registry with brand_name, atc_code, active_substances, interactions_text (FI text)
- `interactions` — Substance-level drug interactions with severity_score (0-3) and description
- `substance_brand_map` — Maps substance names to brand names
- `epha_interactions` — EPha curated ATC-to-ATC interactions with risk_class (A/B/C/D/X), effect, mechanism, measures
- `class_keywords` — ATC class prefix → keyword mappings for class-level FI text search
- `cyp_rules` — CYP enzyme rules (enzyme, text_pattern, role=inhibitor/inducer, atc_prefix, substance)

### Key Patterns

- **Multi-tenant language**: `MyActionCreator` (app/actions/) intercepts requests, sets language based on hostname
- **Dependency injection**: Guice — databases via `@NamedDatabase`, config via `@Inject Config`
- **Drug interactions**: `InteractionsData.getInstance()` runs all tiers for each drug pair via `InteractionsSearch.java` querying `interactions.db`: EPha curated (epha_interactions table), substance-level (interactions table), ATC class-level (class_keywords table + FI text), CYP enzyme (cyp_rules table). All keywords, CYP rules, and EPha data come from database tables (not hardcoded). Bidirectional results shown with Gegenrichtung hints when severity differs across directions. EPha API provides supplementary risk scoring.
- **Async**: Controllers return `CompletionStage<Result>` for non-blocking operations
- **ViewContext**: Passes UI state (logo, feature flags, analytics ID) to Twirl templates

### Frontend

TypeScript files in `app/assets/javascripts/` compile to JS via SBT Web plugin. Module format is SystemJS. Key files:
- `swiyu-login.ts` — OID4VP login widget with QR code and polling
- `amiko.prescriptions.ts` — E-prescription management
- `amiko.searchdb.coffee` — Search autocomplete

Static JS libraries (jQuery 2.2, Typeahead, QRCode.js) are in `public/javascripts/`.

### Feature Flags (conf/application.conf)

- `feature.interactions` — Show drug interactions tab
- `feature.prescriptions` — Show prescription module
- `feature.adswiss_test` — Toggle ADSwiss test/production environment
- `feature.prescription_import_origin` — Allowed origin for prescription imports

### CI/CD

GitHub Actions workflows in `.github/workflows/`:
- `build.yml` — Builds on push, uploads artifact
- `release.yml` — Builds on tags, creates GitHub release

Both inject secrets (HIN_CLIENT_ID, HIN_CLIENT_SECRET, CERTIFACTION_SERVER) by modifying OAuthController.java at build time.

### Footer

The copyright year in `app/views/index.scala.html` is generated dynamically using `@java.time.Year.now().getValue` so it always shows the current year.

## Deployment

Production uses Daemontools service management with Apache reverse proxy to port 9000. SSL via Let's Encrypt.

## Editor

Use vim as the editor.
