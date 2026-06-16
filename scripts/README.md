# ontouml2alloy bundle

The JAR ships a single Node.js bundle of the OntoUML→Alloy CLI so end users do not need a clone of [ontouml-js](https://github.com/OntoUML/ontouml-js). The committed artefacts live under `src/main/resources/ontouml2alloy/`.

## Files (under `src/main/resources/ontouml2alloy/`)

- `ontouml2alloy-cli.bundle.js`: esbuild bundle of `ontouml-js/dist/libs/ontouml2alloy/ontouml2alloy-cli.js` with transitive dependencies inlined
- `VERSION`: `ontouml-js` git SHA at build time, with a `-dirty` suffix if the clone had uncommitted changes. Written by `scripts/refresh-ontouml2alloy.sh`. **Not read or verified by the JVM.**

## Runtime requirement

The host running the JAR must have **Node.js 18+** on `PATH`.

## Refresh prerequisites (maintainers)

Refreshing the bundle needs **Node.js 18+**, **npm**, and **npx** (for pinned `esbuild`). The refresh script runs `npm ci --legacy-peer-deps` in `ontouml-js` because npm 7+ otherwise rejects a known peer-range mismatch in upstream’s lockfile; no edits to upstream `package.json` are required.

## Refreshing the bundle

From the `simulation-without-tears` project root, with a sibling clone of `ontouml-js`, run: 

```bash
scripts/refresh-ontouml2alloy.sh
```

The script defaults to `../ontouml-js` relative to the project root; if projects are not in the same parent folder, override with `ONTOUML_JS=...`: 

```bash
ONTOUML_JS=./../ontouml-js scripts/refresh-ontouml2alloy.sh
```

