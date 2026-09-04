# Documentation standard

## Durable documentation

Documentation changes with the behavior, public contract, or benchmark harness
it describes. Stale commands and claims are defects.

Each document must:

- have exactly one H1 and descriptive H2 sections;
- state purpose, scope, status, audience, and authority where ambiguity matters;
- use concise prose, stable relative links, and alt text for meaningful images;
- give exact prerequisites, commands, expected outcomes, and artifact paths;
- distinguish normative rules from examples and rationale;
- identify versions, SHAs, dates, and evidence boundaries for mutable claims;
- state limitations, failure modes, and anything `NOT RUN` or `BLOCKED`;
- avoid placeholders, stale examples, duplicate authority, and unexplained jargon.

Validate links and runnable examples before declaring documentation complete.
Includes and fragments must make sense in their consuming context.

## README content

As applicable, a durable README covers overview, status, prerequisites,
architecture or entry points, configuration, exact usage/test commands,
artifact locations, known limitations, and contribution workflow.

Link to an authoritative local rule rather than copying it. Preserve design
notes as decision history after implementation and label superseded content.

## Generated and sensitive content

- Label generated material and its generator. Edit the source rather than a
  generated registry/report unless repository rules say otherwise.
- Never put secrets, tokens, credentials, private environment values, sensitive
  absolute paths, or confidential data in docs, metadata, comments, examples,
  Git history, or benchmark artifacts.
- Human contributors remain responsible for reviewing agent-assisted text and
  ensuring every claim is defensible.
