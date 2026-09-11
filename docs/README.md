# Aura Detector Internal Documentation

**Audience:** AI agents and project implementers. This folder is not public-facing project copy.  
**Current project stage:** Build Guide Step 5 — the Android subject-overlay baseline is implemented and installed; physical alignment, selection, and aura interactions remain next.
**What has been done:** planning documents, Android settings shell, server health/protocol scaffold, latest-frame camera/WebSocket transport, a working camera preview, a live YOLO frame-state acknowledgement, and a compiled/installed subject overlay now exist.
**What happens next:** validate a real person box/contour alignment, then add tap selection while keeping the private hotspot as the known-good network fallback.

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
| 0. Planning | Complete | Product, architecture, protocol, UX, test, operations, backlog, and decisions are documented. |
| 1. Environment / structure | Source exists; manual verification pending | Android settings shell and Python server scaffold exist. Record actual SDK, Python/CUDA, device, and LAN setup in TRD and operations. |
| 2. Transport | Physically validated on private hotspot | Camera latest-frame JPEG sender, v1 hello/ack, token entry, frame ordering, status HUD, successful install, and camera preview are verified. |
| 3. Vision and overlay | Overlay baseline installed; physical alignment/person-track acceptance pending | `yolo26n-seg.pt` loads once, warms up, decodes JPEGs, filters COCO person, tracks with BoT-SORT, caps six subjects, and emits normalized boxes/contours. Android preserves the original analysis frame format and uses a UI-thread-cached CameraX transform to render a box or contour plus temporary subject ID. The crop/rotation experiment was reverted after a detection regression. Validate with a walking person. |
| 4. Aura interaction | Not started | Record implemented scan/value behavior and UX changes. |
| 5. Performance and release | Not started | Record measured p50/p95, fallback configuration, demo evidence, and release readiness. |
