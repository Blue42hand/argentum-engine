"""Oracle-identity regression for `check-card-printing.py`.

Run from the repo root: `python3 -m unittest scripts.test_check_card_printing`.
"""

import importlib.machinery
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SCRIPTS_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPTS_DIR))
loader = importlib.machinery.SourceFileLoader(
    "check_card_printing", str(SCRIPTS_DIR / "check-card-printing.py")
)
spec = importlib.util.spec_from_loader(loader.name, loader)
checker = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = checker
loader.exec_module(checker)

REANIMATE_ID = "a044474a-cd72-4e9d-bd8d-a08f2de9cdc0"
PREPARE_ID = "6cb8b8c4-0674-4f14-9d89-010969fbb80e"
# Synthetic IDs; the reversible_card shape is based on REX #21 Plains // Plains.
PLAINS_ID = "9d5b7030-2c89-4a4a-9d17-650ea2b4c8ef"
OTHER_FACE_ID = "70f24f21-17e7-4293-9f18-76e8a65784af"


def card(set_code: str, oracle_id: str, released_at: str) -> dict:
    return {
        "set": set_code,
        "set_name": set_code.upper(),
        "set_type": "expansion",
        "collector_number": "1",
        "released_at": released_at,
        "rarity": "rare",
        "oracle_id": oracle_id,
        "id": f"{set_code}-{oracle_id}",
    }


class ExactOracleIdentityTest(unittest.TestCase):
    def setUp(self):
        self.cache = tempfile.TemporaryDirectory()
        self.addCleanup(self.cache.cleanup)
        patcher = patch.object(checker, "CACHE_ROOT", Path(self.cache.name))
        patcher.start()
        self.addCleanup(patcher.stop)
        self.tmp = card("tmp", REANIMATE_ID, "1997-10-14")
        self.dsc = card("dsc", REANIMATE_ID, "2024-09-27")
        self.sos = card("sos", PREPARE_ID, "2026-04-24")
        self.sos.update({
            "name": "Grave Researcher // Reanimate",
            "layout": "prepare",
            "card_faces": [{"name": "Grave Researcher"}, {"name": "Reanimate"}],
        })

    def test_fresh_search_keeps_real_reprints_and_excludes_same_name_back_face(self):
        calls = []

        def get(url):
            calls.append(url)
            if "/cards/named?exact=Reanimate" in url:
                return {"name": "Reanimate", "oracle_id": REANIMATE_ID}
            if "/cards/search" in url:
                return {"data": [self.tmp, self.dsc, self.sos], "has_more": False}
            self.fail(f"unexpected Scryfall URL: {url}")

        with patch.object(checker, "scryfall_get", side_effect=get):
            rows = checker.fetch_printings("Reanimate", refresh=False)

        self.assertEqual([p.set_code for p in rows], ["tmp", "dsc"])
        self.assertEqual(checker.expected_canonical(rows).set_code, "tmp")
        self.assertEqual(len(calls), 2)
        cached = json.loads((Path(self.cache.name) / "reanimate.json").read_text())
        self.assertEqual({p["oracle_id"] for p in cached}, {REANIMATE_ID})

    def test_legacy_mixed_cache_is_filtered_without_trusting_its_name(self):
        rows = [
            checker.Printing(
                set_code=x["set"], set_name=x["set_name"], set_type=x["set_type"],
                collector_number=x["collector_number"], released_at=x["released_at"],
                rarity=x["rarity"], oracle_id=x["oracle_id"], scryfall_id=x["id"],
            ).__dict__
            for x in (self.tmp, self.sos)
        ]
        (Path(self.cache.name) / "reanimate.json").write_text(json.dumps(rows))

        with patch.object(checker, "scryfall_get", return_value={"oracle_id": REANIMATE_ID}) as get:
            actual = checker.fetch_printings("Reanimate", refresh=False)

        self.assertEqual([p.set_code for p in actual], ["tmp"])
        get.assert_called_once()

    def test_wrong_card_only_fails_closed(self):
        def get(url):
            if "/cards/named?" in url:
                return {"oracle_id": REANIMATE_ID}
            return {"data": [self.sos], "has_more": False}

        with patch.object(checker, "scryfall_get", side_effect=get):
            with self.assertRaisesRegex(ValueError, "no printings for Oracle ID"):
                checker.fetch_printings("Reanimate", refresh=False)

    def test_paginated_search_retains_valid_printings_and_rejects_prepare_back_face(self):
        calls = []

        def get(url):
            calls.append(url)
            if "/cards/named?exact=Reanimate" in url:
                return {"name": "Reanimate", "oracle_id": REANIMATE_ID}
            if "page=2" in url:
                return {"data": [self.dsc, self.sos], "has_more": False}
            if "/cards/search" in url:
                return {
                    "data": [self.tmp, self.sos],
                    "has_more": True,
                    "next_page": "https://api.scryfall.com/cards/search?page=2",
                }
            self.fail(f"unexpected Scryfall URL: {url}")

        with patch.object(checker, "scryfall_get", side_effect=get):
            rows = checker.fetch_printings("Reanimate", refresh=True)

        self.assertEqual([p.set_code for p in rows], ["tmp", "dsc"])
        self.assertEqual(len(calls), 3)
        self.assertEqual(calls[-1], "https://api.scryfall.com/cards/search?page=2")

    def test_reversible_card_matches_face_oracle_id_without_top_level_id(self):
        rex = card("rex", None, "2026-04-24")
        rex.update({
            "name": "Plains // Plains",
            "layout": "reversible_card",
            "card_faces": [
                {"name": "Plains", "oracle_id": PLAINS_ID},
                {"name": "Plains", "oracle_id": OTHER_FACE_ID},
            ],
        })
        wrong = card("sos", PREPARE_ID, "2026-04-24")
        wrong.update({
            "name": "Other Card // Plains",
            "layout": "prepare",
            "card_faces": [
                {"name": "Other Card"},
                {"name": "Plains", "oracle_id": PLAINS_ID},
            ],
        })

        def get(url):
            if "/cards/named?exact=Plains" in url:
                return {"name": "Plains", "oracle_id": PLAINS_ID}
            return {"data": [card("lea", PLAINS_ID, "1993-08-05"), rex, wrong],
                    "has_more": False}

        with patch.object(checker, "scryfall_get", side_effect=get):
            rows = checker.fetch_printings("Plains", refresh=True)

        self.assertEqual([p.set_code for p in rows], ["lea", "rex"])
        self.assertEqual(checker.expected_canonical(rows).set_code, "lea")
        self.assertEqual(rows[1].face_oracle_ids, (PLAINS_ID, OTHER_FACE_ID))

    def test_legacy_cache_without_face_ids_is_refetched(self):
        legacy_rex = checker.Printing(
            set_code="rex", set_name="REX", set_type="expansion",
            collector_number="21", released_at="2026-04-24", rarity="common",
            oracle_id=None, scryfall_id="rex-21",
        )
        (Path(self.cache.name) / "plains.json").write_text(json.dumps([legacy_rex.__dict__]))
        fresh_rex = card("rex", None, "2026-04-24")
        fresh_rex.update({
            "layout": "reversible_card",
            "card_faces": [{"name": "Plains", "oracle_id": PLAINS_ID}],
        })
        calls = []

        def get(url):
            calls.append(url)
            if "/cards/named?exact=Plains" in url:
                return {"name": "Plains", "oracle_id": PLAINS_ID}
            return {"data": [fresh_rex], "has_more": False}

        with patch.object(checker, "scryfall_get", side_effect=get):
            rows = checker.fetch_printings("Plains", refresh=False)

        self.assertEqual([p.set_code for p in rows], ["rex"])
        self.assertEqual(len(calls), 2)
        saved = json.loads((Path(self.cache.name) / "plains.json").read_text())
        self.assertEqual(saved[0]["face_oracle_ids"], [PLAINS_ID])


if __name__ == "__main__":
    unittest.main()
