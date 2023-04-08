#!/usr/bin/env python3

import glob
import os
import re
import sys
import unittest

THIS_DIR = os.path.abspath(os.path.dirname(os.path.realpath(__file__)))
TARGET_DIR = os.path.join(THIS_DIR, 'tests', 'python')

# Return a list of unittest.TestCase
def _collect_tests(suite, testCases = []):
    if (isinstance(suite, unittest.TestCase)):
        testCases.append(suite)
        return testCases

    if (not isinstance(suite, unittest.suite.TestSuite)):
        raise ValueError("Unknown test type: %s" % (str(type(suite))))

    for testObject in suite:
        _collect_tests(testObject, testCases)

    return testCases

def _collect_test_directories(root_dir):
    test_dirs = []
    for path in glob.glob(f'{root_dir}/*/**/', recursive=True):
        if os.path.basename(os.path.dirname(path)) in ['__pycache__']:
            continue
        test_dirs.append(path)
    return test_dirs

def main(pattern = None):
    runner = unittest.TextTestRunner(verbosity = 3)
    test_dirs = _collect_test_directories(TARGET_DIR)
    testCases = []

    for test_dir in test_dirs + [TARGET_DIR]:
        discoveredSuite = unittest.TestLoader().discover(test_dir)
        testCases = testCases + _collect_tests(discoveredSuite)

    print(len(testCases))

    tests = unittest.suite.TestSuite()

    for testCase in testCases:
        if (isinstance(testCase, unittest.loader._FailedTest)):
            print('Failed to load test: %s' % (testCase.id()))
            print(testCase._exception)
            continue

        if (pattern is None or re.search(pattern, testCase.id())):
            tests.addTest(testCase)
        else:
            print("Skipping %s because of match pattern." % (testCase.id()))

    if not runner.run(tests).wasSuccessful():
        sys.exit(1)

def _load_args(args):
    executable = args.pop(0)
    if (len(args) >  1 or ({'h', 'help'} & {arg.lower().strip().replace('-', '') for arg in args})):
        print("USAGE: python3 %s [test pattern]" % (executable), file = sys.stderr)
        print('The test pattern will be used directly in re.search() to see if a test will be run.', file = sys.stderr)
        sys.exit(1)

    pattern = None
    if (len(args) > 0):
        pattern = args.pop(0)

    return (pattern, )

if __name__ == '__main__':
    main(*_load_args(sys.argv))
