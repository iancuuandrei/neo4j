# Contribution standards

## Purpose

These are the self-contained operating rules for research and upstream-ready
work in this personal Neo4j fork. Agents should be able to comply without
opening an external link.

## Standards map

| Standard | Use it when |
| --- | --- |
| [`EVIDENCE.md`](EVIDENCE.md) | Auditing, testing, measuring, qualifying, or reporting claims |
| [`SPECIFICATIONS.md`](SPECIFICATIONS.md) | Defining consequential behavior or an experiment contract |
| [`BENCHMARKS.md`](BENCHMARKS.md) | Designing, running, analyzing, or reporting performance work |
| [`DOCUMENTATION.md`](DOCUMENTATION.md) | Writing durable technical or research documentation |
| [`UPSTREAM_PULL_REQUESTS.md`](UPSTREAM_PULL_REQUESTS.md) | Graduating research and preparing a Neo4j contribution |
| [`SOURCES.md`](SOURCES.md) | Reviewing first-party evidence and rationale behind these rules |

## Boundary

These files belong to the personal research workspace, not an upstream Neo4j
diff. A clean contribution branch contains only the specific production change,
appropriate tests, and documentation needed by that contribution.

The current repository's `CONTRIBUTING.md`, build files, and nearer module
instructions remain authoritative for repository-specific mechanics.
