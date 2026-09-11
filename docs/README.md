# Aura Detector Internal Documentation

**Audience:** AI agents and project implementers. This folder is not public-facing project copy.  
**Current project stage:** Stage 0 — planning complete; no application code, Android project, server, model artifact, benchmark, or deployment exists yet.  
**What has been done:** product scope, architecture, protocol contract, UX behavior, acceptance plan, security boundaries, delivery order, and initial decisions are documented.  
**What happens next:** begin only with Delivery Slice 0 in [DELIVERY_BACKLOG.md](DELIVERY_BACKLOG.md); update the named documents as each slice is implemented and verified.

This folder is the source of truth before implementation begins. All documents describe the **MVP** unless a section explicitly says post-MVP.

| Document | What it tells the AI / when to update it |
|---|---|
| [PRD.md](PRD.md) | Required behavior and scope; update when the user changes a product requirement or priority. |
| [TRD.md](TRD.md) | System shape and technical constraints; update when implementation changes architecture, state, dependencies, or configuration. |
| [WEBSOCKET_PROTOCOL.md](WEBSOCKET_PROTOCOL.md) | Exact phone/PC contract; update before changing any message or protocol behavior. |
| [UX_SPEC.md](UX_SPEC.md) | Interaction and visual-state contract; update before changing gestures, feedback, or failure presentation. |
| [TEST_AND_ACCEPTANCE.md](TEST_AND_ACCEPTANCE.md) | What proves work is correct; update when a requirement gains or changes an acceptance condition. |
| [SECURITY_PRIVACY_OPERATIONS.md](SECURITY_PRIVACY_OPERATIONS.md) | Data boundaries, safe operation, and demo recovery; update when data, network exposure, or operations change. |
| [DELIVERY_BACKLOG.md](DELIVERY_BACKLOG.md) | The next smallest useful slice; mark a slice complete only after its stated evidence exists. |
| [DECISIONS.md](DECISIONS.md) | Why significant choices were made; append an ADR when a decision is accepted, rejected, or reversed. |

Related project material:

- [Product concept and event documentation](../USELESS_PROJECTS_DOCUMENTATION.md)
- [Detailed product plan](../aura-radiation-monitor-plan.md)
- [Practical build guide](../AURA_DETECTOR_BUILD_GUIDE.md)

## Document Rules

- Update the relevant source document in the same change as any decision or behavior change.
- Requirements use IDs (`FR-*`, `NFR-*`) so tests and backlog items can point to them.
- A statement marked **decision pending** must be resolved before implementation depends on it.
- Do not document features that have not been approved as scope.

## Stage Ledger

| Stage | Status | Evidence / documentation to update |
|---|---|---|
| 0. Planning | Complete | This documentation set exists; no executable artifact exists. |
| 1. Environment | Not started | Record actual SDK, Python/CUDA, device, and LAN setup in TRD and operations. |
| 2. Transport | Not started | Implement and verify the v1 protocol; update protocol if reality requires a compatible correction. |
| 3. Vision and overlay | Not started | Record selected model, benchmark, coordinate behavior, and acceptance evidence. |
| 4. Aura interaction | Not started | Record implemented scan/value behavior and UX changes. |
| 5. Performance and release | Not started | Record measured p50/p95, fallback configuration, demo evidence, and release readiness. |
