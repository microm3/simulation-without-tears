# Evaluation

This folder holds the data, code, and notebooks behind the **Evaluation** section of the article.

## Map between article's Evaluation chapter and directories


| Article subsection                                  | Folder                                                                                | What is there                                                |
| --------------------------------------------------- | ------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| *Transformation*                                    | `[transformation-verification/](transformation-verification)`                         | Catalogue transformation + parse/execute/SAT verification    |
| *Simulation Templates* (single-scenario robustness) | `[scenario-verification/](scenario-verification)`                                     | Single-scenario runs for the Criminal Investigation model[2] |
| *Composability*                                     | `[scenario-verification/](scenario-verification)`                                     | Pairwise scenario-combination runs and the policy refinement |
| *Use Case Illustration* (Cases A & B)               | `[scenario-verification/models/musso_2021/](scenario-verification/models/musso_2021)` | The Musso employment model[3] used in the worked example     |


The dataset (the 144-model JSON distribution of the OntoUML/UFO Catalog[1]) is obtained via
`[catalog-download/](catalog-download)`. The catalogue models and transformed files are not included here due to some having licenses restricting redistribution; the notebooks can be used to download and regenerate artefacts.

## `catalog-download/`

Downloads the OntoUML/UFO Catalog models used as the dataset.

- `catalog_download.ipynb`: runner notebook
- `ontouml_downloads/`: the downloaded per-model JSON files (the 144-model dataset).

## `transformation-verification/`

Validation of the updated `ontouml2alloy` transformation against the full catalogue.

- `results/`: outputs of the **updated** transformation.
  - `batch-alloy-output/<model>/`: transformation output per model (`main.als`, `world_structure.als`,
  `ontological_properties.als`, `theme.thm`,  `transformation_metadata.json)`
  - `verification/verification_results.json`: per-model parse/execute/SAT-UNSAT outcomes and
  class-retention figures. This is the source for the *transformation outcomes* and
  *class retention* tables, and the per-model breakdown referred to in the article.
- `results-baseline/`: verification run on the **pre-update** transformation
- `tranformation_verification.ipynb`: runner and results for updated transformation.
- `transformation_verification_baseline.ipynb`: pre-update baseline.

## `scenario-verification/`

Single-scenario robustness and pairwise composability, run mainly against the Criminal
Investigation model.

- `scenario-verification.ipynb`: runner/result notebook: single-scenario robustness, the initial target-property policy (Stage 1), the world-shape-only policy (Stage 2), and the class-binding-overlap diagnostics.
- `models/`: the input models: `fumagalli2022criminal-investigation/`[2] (robustness + composability) and `musso_2021/`[3] (use-case illustration).
- `target_property_combination_policy.json`: the initial composability policy removed from source code but retained as json map for evaluation run.
- `results/<model>/`: the run results, as JSON Lines:  
Each line records the scenario(s), sampled `parameters`, rendered Alloy and natural-language description, `status`/`satisfiable`, and timing.
  - `scenario_batch_*_n500_seed42.jsonl`: single-scenario robustness runs (up to 500 bindings per
  scenario).
  - `scenario_batch_combined_*.jsonl`: **allowed** scenario pairs under the final rule;
  `shared` vs `disjoint` in the filename mark the class-binding-overlap condition.
  - `scenario_batch_combined_disallowed_*.jsonl`: pairs **disallowed** under the initial policy,
  re-run to test whether the restriction was justified.

## Re-running the experiments

Experiments can be rerun modifying the relevant flags in the notebooks. Refer to the top-level `[README](../README.md)` for build prerequisites.

---

[1] Barcelos et al., *OntoUML/UFO Catalog* (2023). Zenodo.  
[2] Fumagalli et al., *Conceptual model visual simulation and the inductive learning of missing domain constraints*. Data & Knowledge Engineering, vol. 140 (2022).  
[3] Musso, *An OntoUML 2.0 to Alloy transformation for the OntoUML Server*. Bachelor's thesis, Universidade Federal do Espírito Santo (2021).