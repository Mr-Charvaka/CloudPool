# ADR 001: Modular Monolith and SPI Provider Architecture

## Status
Accepted

## Context
The CloudPool system was initially structured as distinct microservices (`cloudpool-auth`, `cloudpool-data`, `cloudpool-network`), but shared the exact same database and extensively copy-pasted core business logic (e.g., `ProjectService`) across all modules. This resulted in a Distributed Monolith antipattern, leading to maintainability issues and risk of split-brain data access.

Additionally, the system tightly coupled infrastructure providers (like Google Drive for storage) directly into the application services, violating the Dependency Inversion Principle.

## Decision
1. **Adopt a Modular Monolith Architecture:**
   - We will transition from fake microservices to a Modular Monolith.
   - `cloudpool-common` will be strictly reserved for cross-cutting concerns, shared abstractions, Service Provider Interfaces (SPIs), Exception definitions, and Value Objects.
   - **No business logic will reside in `cloudpool-common`.**
   - Distinct business domains (Auth, Compute, Storage) will reside in their respective modules, and inter-module communication will be managed via internal Spring beans or event-driven mechanisms (RabbitMQ).

2. **Implement SPI Provider Pattern for Infrastructure:**
   - Define a `StorageProvider` interface in `cloudpool-common`.
   - Implementations (e.g., `GoogleDriveProvider`, `AwsS3Provider`) will be injected into a core `StorageService`.
   - The Application-level Encryption layer will sit above the `StorageProvider`, ensuring provider-agnostic encryption.

## Consequences
- **Pros:** Greatly simplifies the build and deployment process. Eliminates copy-pasted code. Decouples business logic from specific cloud vendors (GCP, AWS).
- **Cons:** Requires strict boundary enforcement to prevent "big ball of mud" coupling. We will mitigate this using ArchUnit tests.
