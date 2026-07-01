# cloud-itonami-4211

Open Business Blueprint for **ISIC Rev.5 4211**: building construction — structural, envelope and finishing work for community-scale projects.

This repository designs a forkable OSS business for community building construction:
run by a qualified operator so a community keeps its own operating records
instead of renting a closed SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a construction robot performs bricklaying, printing, lifting and inspection on site under an actor that proposes
actions and an independent **Construction Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
operating on site, near the public or at height) require human sign-off.

## Core Contract

```text
intake + identity + identity records
        |
        v
Advisor -> Construction Governor -> proceed, hold, or human approval
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `4211`). Required capabilities:

- `:robotics`
- `:identity`
- `:forms`
- `:dmn`
- `:bpmn`
- `:audit-ledger`

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
