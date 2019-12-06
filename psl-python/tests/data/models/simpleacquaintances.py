#!/usr/bin/env python3

import os

from pslpython.model import Model
from pslpython.partition import Partition
from pslpython.predicate import Predicate
from pslpython.rule import Rule

MODEL_NAME = 'simple-acquaintances'
ADDITIONAL_PSL_OPTIONS = {'log4j.threshold': 'ERROR'}

DEFAULT_DATA_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'simple-acquaintances', 'data'))

def run(data_dir = DEFAULT_DATA_DIR):
    model = Model(MODEL_NAME)

    # Add Predicates

    knows_predicate = Predicate('Knows', closed = False, size = 2)
    model.add_predicate(knows_predicate)

    likes_predicate = Predicate('Likes', closed = True, size = 2)
    model.add_predicate(likes_predicate)

    lived_predicate = Predicate('Lived', closed = True, size = 2)
    model.add_predicate(lived_predicate)

    # Add Data

    path = os.path.join(data_dir, 'knows_obs.txt')
    knows_predicate.add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(data_dir, 'lived_obs.txt')
    lived_predicate.add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(data_dir, 'likes_obs.txt')
    likes_predicate.add_data_file(Partition.OBSERVATIONS, path)

    path = os.path.join(data_dir, 'knows_targets.txt')
    knows_predicate.add_data_file(Partition.TARGETS, path)

    path = os.path.join(data_dir, 'knows_truth.txt')
    knows_predicate.add_data_file(Partition.TRUTH, path)

    # Add Rules
    model.add_rule(Rule('20: Lived(P1, L) & Lived(P2, L) & (P1 != P2) -> Knows(P1, P2) ^2'))
    model.add_rule(Rule('5: Lived(P1, L1) & Lived(P2, L2) & (P1 != P2) & (L1 != L2) -> !Knows(P1, P2) ^2'))
    model.add_rule(Rule('10: Likes(P1, L) & Likes(P2, L) & (P1 != P2) -> Knows(P1, P2) ^2'))
    model.add_rule(Rule('5: Knows(P1, P2) & Knows(P2, P3) & (P1 != P3) -> Knows(P1, P3) ^2'))
    model.add_rule(Rule('Knows(P1, P2) = Knows(P2, P1) .'))
    model.add_rule(Rule('5: !Knows(P1, P2) ^2'))

    # Run Inference
    results = model.infer(psl_config = ADDITIONAL_PSL_OPTIONS)

    return results

if (__name__ == '__main__'):
    results = run()

    for (predicate, frame) in results.items():
        print("--- %s ---" % (predicate.name()))
        print(frame)
