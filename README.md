# Simulation Without Tears
Kotlin/Maven tool to support simulation of OntoUML models.

## What it does

1. **Model preparation**: accepts an OntoUML `.json` model and transforms it to Alloy (`.als`) using a bundled [`ontouml-js.ontouml2alloy`](https://github.com/OntoUML/ontouml-js)-component (requiring Node.js), or takes a pre-transformed `.als` file directly. A `transformation_metadata.json` and a colour-coded `theme.thm` are generated alongside the model.

2. **Scenario selection**: a Swing-based GUI lets the user browse and configure simulation scenarios from a built-in catalogue of 23 scenarios (`simulation_scenarios.json`). Scenarios are parameterised (class references, associations, integers, enums) and up to five non-conflicting scenarios can be combined into a single predicate, please not that thorough testing has not been performed for combinations of more than two scenarios. The generated Alloy predicate can be previewed and manually edited before running.

3. **Solving and visualisation**: the configured constraint is appended to the model and solved with the Alloy Analyzer (using the default solver SAT4J). Satisfying instances are shown in the Alloy VizGUI with the generated theme applied. The user can step through further instances or re-run a different scenario.

## Download

A pre-built JAR is available at [`target/simulation-without-tears-1.0-SNAPSHOT.jar`](target/simulation-without-tears-1.0-SNAPSHOT.jar). If you use the JAR, jump directly to [Running](#running) below.

## Prerequisites

- **Java 17** or later
- **Node.js 18** or later (required at runtime for OntoUML to Alloy transformation; while the ontouml-js `ontouml2alloy` library is bundled in the JAR, Node.js must be on the PATH)

## Building from source

Requires **Maven 3.x.**

```bash
git clone <repo-url>
cd simulation-without-tears
mvn package
```

This produces:
- `target/simulation-without-tears-1.0-SNAPSHOT.jar`: the runnable JAR
- `target/sat4j-sources/`: SAT4J source JARs (for LGPL compliance, include these when redistributing the JAR)

## Running

Open a file-picker GUI (no arguments):
```bash
java -jar target/simulation-without-tears-1.0-SNAPSHOT.jar
```

Pass an OntoUML JSON model directly:
```bash
java -jar target/simulation-without-tears-1.0-SNAPSHOT.jar path/to/model.json
```


## Third-party licenses

This project uses third-party components. See [THIRD-PARTY.md](THIRD-PARTY.md) for the full list and attribution.

The default SAT solver, **SAT4J**, is licensed under the GNU LGPL 2.1; see [licenses/sat4j.txt](licenses/sat4j.txt).