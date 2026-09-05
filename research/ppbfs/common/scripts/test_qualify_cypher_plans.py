import importlib.util
from pathlib import Path


SCRIPT = Path(__file__).with_name("qualify_cypher_plans.py")
SPEC = importlib.util.spec_from_file_location("qualify_cypher_plans", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


def test_operators_flattens_preorder():
    plan = {
        "operatorType": "ProduceResults",
        "children": [
            {
                "operatorType": "StatefulShortestPath(Into, Trail)",
                "children": [{"operatorType": "NodeByLabelScan", "children": []}],
            }
        ],
    }
    assert MODULE.operators(plan) == [
        "ProduceResults",
        "StatefulShortestPath(Into, Trail)",
        "NodeByLabelScan",
    ]
