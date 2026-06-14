# postgres stack module

- Module id: `postgres`
- Module repo: `postgres-stack-module`
- Source repo: none declared
- Lifecycle: `active`

## Owned overlays
- `stack.compose/postgres.yml`
- `stack.config/postgres`

## Dependencies
- `stack-foundation`

## Validation

```sh
./tests/validate.sh
```

## Lifecycle

`active` modules are expected to keep `stack.module.json`, owned overlays, and `tests/validate.sh` in sync.
