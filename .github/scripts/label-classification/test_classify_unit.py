#!/usr/bin/env python3
"""
Unit tests for classify.py.

These are the offline counterpart to test_classify.py: that script measures
classification *accuracy* against real issues and needs network, `gh` and the
embedding model. This one exercises the decision logic with hand-made vectors,
so it runs in CI in a second with only pyyaml and numpy installed.

    python3 .github/scripts/label-classification/test_classify_unit.py
"""

import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import numpy as np

# The directory is not a package (classify.py is invoked as a script), so put
# it on the path explicitly — this then runs from any working directory.
sys.path.insert(0, str(Path(__file__).parent))

import classify  # noqa: E402


# Orthogonal unit vectors, so cosine similarity against V[0] is exactly 1.0 for
# itself and 0.0 for every other one. Partial similarities are built by mixing.
V = np.eye(4)


def mixed(primary, weight):
    """A unit vector whose cosine similarity with V[primary] is `weight`."""
    other = (primary + 1) % len(V)
    vec = weight * V[primary] + np.sqrt(1 - weight ** 2) * V[other]
    return vec


def area_config(labels, threshold=0.35, max_labels=4):
    return {"area_labels": {"threshold": threshold, "max_labels": max_labels, "labels": labels}}


class DirectoriesOfTest(unittest.TestCase):

    def test_files_are_collapsed_to_their_parent_directory(self):
        self.assertEqual(
            classify.directories_of(["app/src/Foo.java", "ui/src/Bar.tsx"]),
            ["app/src", "ui/src"])

    def test_siblings_collapse_to_a_single_entry(self):
        # The whole point: 30 files in one package must not spend 30 slots.
        paths = [f"app/src/storage/File{i}.java" for i in range(30)]
        self.assertEqual(classify.directories_of(paths), ["app/src/storage"])

    def test_first_seen_order_is_preserved(self):
        self.assertEqual(
            classify.directories_of(["z/a.java", "a/b.java", "z/c.java"]),
            ["z", "a"])

    def test_a_root_level_file_stands_in_for_itself(self):
        self.assertEqual(classify.directories_of(["README.md"]), ["README.md"])

    def test_empty_input_yields_no_directories(self):
        self.assertEqual(classify.directories_of([]), [])


class BuildTextTest(unittest.TestCase):

    def test_issue_text_is_title_and_body(self):
        self.assertEqual(classify.build_text("Title", "Body"), "Title\n\nBody")

    def test_no_directories_inserted_when_file_list_is_empty(self):
        self.assertEqual(classify.build_text("Title", "Body", []), "Title\n\nBody")

    def test_directories_sit_between_the_title_and_the_body(self):
        # Load-bearing ordering: the model reads only the first 256 word pieces,
        # so directories placed after the body would never be seen.
        text = classify.build_text("Title", "Body", ["app/src/Foo.java", "ui/src/Bar.tsx"])
        self.assertEqual(text, "Title\n\napp/src\nui/src\n\nBody")

    def test_only_the_first_MAX_PR_DIRS_directories_are_used(self):
        paths = [f"src/pkg{i}/File.java" for i in range(classify.MAX_PR_DIRS + 10)]
        text = classify.build_text("Title", "Body", paths)
        self.assertIn(f"src/pkg{classify.MAX_PR_DIRS - 1}\n", text)
        self.assertNotIn(f"src/pkg{classify.MAX_PR_DIRS}\n", text)

    def test_text_is_truncated_to_the_character_cap(self):
        text = classify.build_text("T", "b" * (classify.MAX_TEXT_CHARS * 2))
        self.assertEqual(len(text), classify.MAX_TEXT_CHARS)

    def test_truncation_applies_after_directories_are_inserted(self):
        text = classify.build_text("T", "b" * classify.MAX_TEXT_CHARS, ["src/Foo.java"])
        self.assertEqual(len(text), classify.MAX_TEXT_CHARS)

    def test_a_long_body_cannot_push_directories_out_of_the_text(self):
        # The regression this layout exists to prevent.
        text = classify.build_text("T", "b" * (classify.MAX_TEXT_CHARS * 2),
                                   ["app/src/storage/Foo.java"])
        self.assertIn("app/src/storage", text)


class ClassifyAreaLabelsTest(unittest.TestCase):

    def test_labels_above_the_default_threshold_are_selected(self):
        config = area_config({"area/hit": {}, "area/miss": {}})
        embeddings = {"area/hit": V[0], "area/miss": V[1]}

        selected, scores = classify.classify_area_labels(V[0], embeddings, config)

        self.assertEqual(selected, {"area/hit"})
        self.assertAlmostEqual(scores["area/hit"], 1.0)
        self.assertAlmostEqual(scores["area/miss"], 0.0)

    def test_per_label_threshold_overrides_the_default(self):
        # 0.30 clears the label's own 0.25 override but not the 0.35 default.
        config = area_config({"area/broad": {"threshold": 0.25}, "area/narrow": {}})
        embeddings = {"area/broad": V[0], "area/narrow": V[0]}

        selected, _ = classify.classify_area_labels(mixed(0, 0.30), embeddings, config)

        self.assertEqual(selected, {"area/broad"})

    def test_selection_is_capped_at_max_labels(self):
        labels = {f"area/l{i}": {} for i in range(4)}
        config = area_config(labels, max_labels=2)
        # Descending similarities, all above threshold.
        embeddings = {f"area/l{i}": mixed(0, 0.9 - 0.1 * i) for i in range(4)}

        selected, _ = classify.classify_area_labels(V[0], embeddings, config)

        self.assertEqual(selected, {"area/l0", "area/l1"})

    def test_selecting_a_child_label_pulls_in_its_parent(self):
        config = area_config({
            "area/storage": {},
            "area/storage/sql": {},
        })
        # The child matches; the parent on its own would score 0.
        embeddings = {"area/storage": V[1], "area/storage/sql": V[0]}

        selected, _ = classify.classify_area_labels(V[0], embeddings, config)

        self.assertEqual(selected, {"area/storage/sql", "area/storage"})

    def test_parents_pulled_in_do_not_consume_the_max_labels_budget(self):
        config = area_config({
            "area/other": {},
            "area/storage": {},
            "area/storage/sql": {},
        }, max_labels=2)
        embeddings = {
            "area/storage/sql": mixed(0, 0.9),
            "area/other": mixed(0, 0.8),
            "area/storage": V[1],
        }

        selected, _ = classify.classify_area_labels(V[0], embeddings, config)

        self.assertEqual(selected, {"area/storage/sql", "area/other", "area/storage"})

    def test_nothing_is_selected_when_every_label_is_below_threshold(self):
        config = area_config({"area/a": {}, "area/b": {}})
        embeddings = {"area/a": V[1], "area/b": V[2]}

        selected, scores = classify.classify_area_labels(V[0], embeddings, config)

        self.assertEqual(selected, set())
        self.assertEqual(len(scores), 2)


class NestedLabelPreferenceTest(unittest.TestCase):
    """When a parent and its child both qualify, the child is the answer and
    the parent is implied — it must not spend a second slot saying less."""

    def test_a_qualifying_child_takes_its_parents_slot(self):
        # Parent outscores the child, and the budget is exactly one: without the
        # preference the parent would take the only slot and the child would be
        # capped.
        config = area_config({"area/storage": {}, "area/storage/sql": {}}, max_labels=1)
        embeddings = {"area/storage": mixed(0, 0.9), "area/storage/sql": mixed(0, 0.5)}

        selected, _ = classify.classify_area_labels(V[0], embeddings, config)

        self.assertEqual(selected, {"area/storage/sql", "area/storage"})

    def test_a_parent_scoring_below_its_picked_child_does_not_cost_a_slot(self):
        config = area_config({
            "area/storage": {}, "area/storage/sql": {}, "area/other": {},
        }, max_labels=2)
        embeddings = {
            "area/storage/sql": mixed(0, 0.9),
            "area/storage": mixed(0, 0.8),
            "area/other": mixed(0, 0.7),
        }

        selected, _ = classify.classify_area_labels(V[0], embeddings, config)

        # Pre-preference this would have been {sql, storage} with area/other
        # capped, even though storage was implied by sql anyway.
        self.assertEqual(selected, {"area/storage/sql", "area/storage", "area/other"})

    def test_a_freed_parent_slot_goes_to_the_next_best_label(self):
        config = area_config({
            "area/storage": {}, "area/storage/sql": {}, "area/a": {}, "area/b": {},
        }, max_labels=2)
        embeddings = {
            "area/storage": mixed(0, 0.9),
            "area/a": mixed(0, 0.8),
            "area/b": mixed(0, 0.7),
            "area/storage/sql": mixed(0, 0.6),
        }

        selected, _ = classify.classify_area_labels(V[0], embeddings, config)

        # sql replaces storage in place; area/b stays capped because the budget
        # is still full with {sql, a}.
        self.assertEqual(selected, {"area/storage/sql", "area/storage", "area/a"})

    def test_two_qualifying_siblings_each_take_a_slot(self):
        config = area_config({
            "area/storage": {}, "area/storage/sql": {}, "area/storage/kafkasql": {},
        }, max_labels=2)
        embeddings = {
            "area/storage": mixed(0, 0.9),
            "area/storage/sql": mixed(0, 0.8),
            "area/storage/kafkasql": mixed(0, 0.7),
        }

        selected, _ = classify.classify_area_labels(V[0], embeddings, config)

        self.assertEqual(selected,
                         {"area/storage/sql", "area/storage/kafkasql", "area/storage"})

    def test_a_parent_is_kept_when_no_child_qualifies(self):
        # The preference narrows a pick; it never lowers a child's threshold.
        config = area_config({"area/AI": {}, "area/AI/MCP": {}})
        embeddings = {"area/AI": V[0], "area/AI/MCP": mixed(0, 0.30)}

        selected, _ = classify.classify_area_labels(V[0], embeddings, config)

        self.assertEqual(selected, {"area/AI"})

    def test_a_grandchild_brings_every_ancestor(self):
        config = area_config({"area/a": {}, "area/a/b": {}, "area/a/b/c": {}})
        embeddings = {"area/a": V[1], "area/a/b": V[1], "area/a/b/c": V[0]}

        selected, _ = classify.classify_area_labels(V[0], embeddings, config)

        self.assertEqual(selected, {"area/a/b/c", "area/a/b", "area/a"})

    def test_a_grandchild_supersedes_a_picked_grandparent(self):
        config = area_config({"area/a": {}, "area/a/b": {}, "area/a/b/c": {}}, max_labels=1)
        embeddings = {"area/a": mixed(0, 0.9), "area/a/b": V[1], "area/a/b/c": mixed(0, 0.5)}

        selected, _ = classify.classify_area_labels(V[0], embeddings, config)

        self.assertEqual(selected, {"area/a/b/c", "area/a/b", "area/a"})

    def test_equal_scores_select_deterministically(self):
        labels = {f"area/l{i}": {} for i in range(4)}
        config = area_config(labels, max_labels=2)
        embeddings = {name: V[0] for name in labels}

        selected, _ = classify.classify_area_labels(V[0], embeddings, config)

        self.assertEqual(selected, {"area/l0", "area/l1"})


class HierarchyTest(unittest.TestCase):

    LABELS = {"area/storage": {}, "area/storage/sql": {}, "area/a": {}, "area/a/b/c": {}}

    def test_a_child_nests_under_its_name_prefix(self):
        self.assertEqual(classify.parent_of("area/storage/sql", self.LABELS), "area/storage")

    def test_a_top_level_label_has_no_parent(self):
        self.assertIsNone(classify.parent_of("area/storage", self.LABELS))

    def test_a_prefix_that_is_not_a_configured_label_is_skipped(self):
        # area/a/b is not configured, so area/a/b/c nests directly under area/a.
        self.assertEqual(classify.parent_of("area/a/b/c", self.LABELS), "area/a")

    def test_a_shared_string_prefix_is_not_nesting(self):
        # Segments, not characters: area/storage-x is a sibling of area/storage.
        self.assertIsNone(classify.parent_of("area/storage-x", self.LABELS))

    def test_ancestors_are_listed_nearest_first(self):
        labels = {"area/a": {}, "area/a/b": {}, "area/a/b/c": {}}
        self.assertEqual(classify.ancestors_of("area/a/b/c", labels), ["area/a/b", "area/a"])


class CappedLabelsTest(unittest.TestCase):
    """The tuning affordance: a label above its threshold that still was not
    assigned must be distinguishable from one that simply scored too low."""

    def test_labels_above_threshold_but_not_selected_are_capped(self):
        config = area_config({"area/a": {}, "area/b": {}, "area/c": {}}, max_labels=2)
        scores = {"area/a": 0.9, "area/b": 0.8, "area/c": 0.7}

        self.assertEqual(
            classify.capped_labels(scores, config, {"area/a", "area/b"}),
            {"area/c"})

    def test_labels_below_threshold_are_not_capped(self):
        config = area_config({"area/a": {}, "area/b": {}}, max_labels=2)
        scores = {"area/a": 0.9, "area/b": 0.1}

        self.assertEqual(classify.capped_labels(scores, config, {"area/a"}), set())

    def test_per_label_thresholds_are_respected(self):
        config = area_config({"area/broad": {"threshold": 0.25}, "area/narrow": {}},
                             max_labels=1)
        scores = {"area/broad": 0.30, "area/narrow": 0.30}

        # Only area/broad cleared its own threshold, so only it can be capped.
        self.assertEqual(classify.capped_labels(scores, config, set()), {"area/broad"})

    def test_a_parent_below_its_own_threshold_is_not_reported_as_capped(self):
        config = area_config({
            "area/storage": {},
            "area/storage/sql": {},
        }, max_labels=4)
        scores = {"area/storage/sql": 0.9, "area/storage": 0.01}

        # The parent was added mechanically, not capped — and it is in selected.
        self.assertEqual(
            classify.capped_labels(scores, config, {"area/storage/sql", "area/storage"}),
            set())

    def test_nothing_is_capped_when_everything_above_threshold_was_selected(self):
        config = area_config({"area/a": {}, "area/b": {}}, max_labels=4)
        scores = {"area/a": 0.9, "area/b": 0.8}

        self.assertEqual(classify.capped_labels(scores, config, {"area/a", "area/b"}), set())


class ClassifyIssueTypeTest(unittest.TestCase):

    CONFIG = {"issue_types": {"threshold": 0.40, "types": {
        "Bug": {"id": "IT_bug"},
        "Feature": {"id": "IT_feature"},
    }}}

    def test_highest_scoring_type_above_threshold_wins(self):
        embeddings = {"Bug": V[0], "Feature": V[1]}

        name, type_id, scores = classify.classify_issue_type(V[0], embeddings, self.CONFIG)

        self.assertEqual(name, "Bug")
        self.assertEqual(type_id, "IT_bug")
        self.assertAlmostEqual(scores["Bug"], 1.0)

    def test_no_type_is_chosen_when_the_best_score_is_below_threshold(self):
        embeddings = {"Bug": mixed(0, 0.30), "Feature": V[1]}

        name, type_id, scores = classify.classify_issue_type(V[0], embeddings, self.CONFIG)

        self.assertIsNone(name)
        self.assertIsNone(type_id)
        self.assertAlmostEqual(scores["Bug"], 0.30)


class IsPullRequestTest(unittest.TestCase):

    def test_a_payload_carrying_the_pull_request_key_is_a_pr(self):
        self.assertTrue(classify.is_pull_request({"number": 1, "pull_request": {"url": "..."}}))

    def test_a_payload_without_the_key_is_an_issue(self):
        self.assertFalse(classify.is_pull_request({"number": 1}))


class GetPrFilesTest(unittest.TestCase):

    def _run_result(self, returncode=0, stdout="", stderr=""):
        return subprocess.CompletedProcess(args=[], returncode=returncode,
                                           stdout=stdout, stderr=stderr)

    def test_returns_one_path_per_line(self):
        with mock.patch.object(classify.subprocess, "run",
                               return_value=self._run_result(stdout="a/B.java\nc/D.tsx\n")):
            self.assertEqual(classify.get_pr_files("o/r", 1), ["a/B.java", "c/D.tsx"])

    def test_blank_lines_are_dropped(self):
        with mock.patch.object(classify.subprocess, "run",
                               return_value=self._run_result(stdout="a/B.java\n\n\nc/D.tsx\n")):
            self.assertEqual(classify.get_pr_files("o/r", 1), ["a/B.java", "c/D.tsx"])

    def test_a_failed_lookup_degrades_to_an_empty_list(self):
        # Classification must still run on title and body alone rather than
        # failing the workflow.
        with mock.patch.object(classify.subprocess, "run",
                               return_value=self._run_result(returncode=1, stderr="boom")):
            self.assertEqual(classify.get_pr_files("o/r", 1), [])

    def test_the_files_endpoint_is_paginated(self):
        with mock.patch.object(classify.subprocess, "run",
                               return_value=self._run_result()) as run:
            classify.get_pr_files("apicurio/registry", 42)

        argv = run.call_args.args[0]
        self.assertIn("--paginate", argv)
        self.assertIn("repos/apicurio/registry/pulls/42/files", argv)


class GetRemovedAreaLabelsTest(unittest.TestCase):

    def _run_result(self, returncode=0, stdout="", stderr=""):
        return subprocess.CompletedProcess(args=[], returncode=returncode,
                                           stdout=stdout, stderr=stderr)

    def test_returns_the_labels_from_unlabeled_events(self):
        with mock.patch.object(classify.subprocess, "run",
                               return_value=self._run_result(stdout="area/ui\narea/rest\n")):
            self.assertEqual(classify.get_removed_area_labels("o/r", 1),
                             {"area/ui", "area/rest"})

    def test_a_label_removed_twice_is_reported_once(self):
        with mock.patch.object(classify.subprocess, "run",
                               return_value=self._run_result(stdout="area/ui\narea/ui\n")):
            self.assertEqual(classify.get_removed_area_labels("o/r", 1), {"area/ui"})

    def test_an_unreadable_history_degrades_to_no_suppression(self):
        # Failing open keeps classification working; failing closed would mean a
        # transient API error silently stops labelling altogether.
        with mock.patch.object(classify.subprocess, "run",
                               return_value=self._run_result(returncode=1, stderr="boom")):
            self.assertEqual(classify.get_removed_area_labels("o/r", 1), set())

    def test_the_events_endpoint_is_paginated(self):
        with mock.patch.object(classify.subprocess, "run",
                               return_value=self._run_result()) as run:
            classify.get_removed_area_labels("apicurio/registry", 42)

        argv = run.call_args.args[0]
        self.assertIn("--paginate", argv)
        self.assertIn("repos/apicurio/registry/issues/42/events", argv)

    def test_the_query_filters_to_unlabeled_events_on_area_labels(self):
        # The lifecycle orchestrator's lifecycle/* churn dominates this
        # timeline; filtering server-side keeps it out of the result entirely.
        with mock.patch.object(classify.subprocess, "run",
                               return_value=self._run_result()) as run:
            classify.get_removed_area_labels("apicurio/registry", 42)

        jq = run.call_args.args[0][run.call_args.args[0].index("--jq") + 1]
        self.assertIn("unlabeled", jq)
        self.assertIn('startswith("area/")', jq)


class LabelsToApplyTest(unittest.TestCase):
    """A human's correction has to outlive the next edit — otherwise the
    classifier reinstates its own mistake and the label history stops being
    trustworthy training data (see #10160)."""

    def test_a_previously_removed_label_is_never_re_added(self):
        self.assertEqual(
            classify.labels_to_apply({"area/ui", "area/rest"}, set(), {"area/ui"}),
            {"area/rest"})

    def test_labels_already_present_are_not_re_applied(self):
        self.assertEqual(
            classify.labels_to_apply({"area/ui", "area/rest"}, {"area/ui"}, set()),
            {"area/rest"})

    def test_a_label_removed_then_manually_restored_stays(self):
        # It is present, so there is nothing to add — and nothing removes it.
        self.assertEqual(
            classify.labels_to_apply({"area/ui"}, {"area/ui"}, {"area/ui"}),
            set())

    def test_removals_of_labels_the_classifier_did_not_pick_are_irrelevant(self):
        self.assertEqual(
            classify.labels_to_apply({"area/rest"}, set(), {"area/ui", "area/avro"}),
            {"area/rest"})

    def test_nothing_to_add_when_every_pick_was_previously_removed(self):
        self.assertEqual(
            classify.labels_to_apply({"area/ui"}, set(), {"area/ui"}), set())


class ApplyLabelsTest(unittest.TestCase):

    def _subcommand_for(self, is_pr):
        with mock.patch.object(classify.subprocess, "run") as run:
            classify.apply_labels("o/r", 7, ["area/ui"], is_pr=is_pr)
        return run.call_args.args[0]

    def test_issues_are_labelled_through_gh_issue_edit(self):
        argv = self._subcommand_for(is_pr=False)
        self.assertEqual(argv[:3], ["gh", "issue", "edit"])
        self.assertIn("area/ui", argv)

    def test_prs_are_labelled_through_gh_pr_edit(self):
        argv = self._subcommand_for(is_pr=True)
        self.assertEqual(argv[:3], ["gh", "pr", "edit"])

    def test_every_label_gets_its_own_call(self):
        with mock.patch.object(classify.subprocess, "run") as run:
            classify.apply_labels("o/r", 7, ["area/ui", "area/rest"], is_pr=True)
        self.assertEqual(run.call_count, 2)

    def test_labels_are_applied_in_sorted_order(self):
        # Input is a set in production and Python randomises string hashing per
        # process, so without sorting the log order varies between runs.
        with mock.patch.object(classify.subprocess, "run") as run:
            classify.apply_labels("o/r", 7, {"area/ui", "area/rest", "area/CI"})

        applied = [call.args[0][-1] for call in run.call_args_list]
        self.assertEqual(applied, ["area/CI", "area/rest", "area/ui"])


class WriteOutputJsonTest(unittest.TestCase):

    def test_payload_round_trips_through_the_file(self):
        payload = {"kind": "pr", "number": 42, "area_label_scores": {"area/ui": 0.51}}
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "scores.json"
            classify.write_output_json(str(path), payload)
            self.assertEqual(json.loads(path.read_text()), payload)


class ConfigTest(unittest.TestCase):
    """The shipped label-descriptions.yml has to satisfy the assumptions the
    classifier makes about it — a typo here silently changes behaviour for
    every issue and PR."""

    @classmethod
    def setUpClass(cls):
        cls.config = classify.load_config()

    def test_every_area_label_has_a_description(self):
        for name, label in self.config["area_labels"]["labels"].items():
            self.assertTrue(label.get("description", "").strip(), f"{name} has no description")

    def test_labels_carry_only_known_keys(self):
        # Nesting comes from the name. A leftover `parent:` or `children:` key
        # would look authoritative while being ignored.
        for name, label in self.config["area_labels"]["labels"].items():
            self.assertLessEqual(set(label), {"description", "threshold"},
                                 f"{name} has unexpected keys")

    def test_every_nested_label_has_its_immediate_parent_configured(self):
        # parent_of tolerates gaps, but the shipped config should not rely on
        # that: a missing middle label is almost always a typo or a rename that
        # was only half done.
        labels = self.config["area_labels"]["labels"]
        for name in labels:
            segments = name.split("/")
            if len(segments) > 2:
                self.assertIn("/".join(segments[:-1]), labels,
                              f"{name} nests under a label that is not configured")

    def test_label_names_are_unique_ignoring_case(self):
        # GitHub label names are case-insensitive; two entries differing only in
        # case would be the same label there.
        names = [name.lower() for name in self.config["area_labels"]["labels"]]
        self.assertEqual(len(names), len(set(names)))

    def test_every_label_is_in_the_area_namespace(self):
        for name in self.config["area_labels"]["labels"]:
            self.assertTrue(name.startswith("area/"), name)

    def test_every_issue_type_has_an_id(self):
        for name, type_config in self.config["issue_types"]["types"].items():
            self.assertTrue(type_config.get("id"), f"{name} has no id")


if __name__ == "__main__":
    unittest.main()
