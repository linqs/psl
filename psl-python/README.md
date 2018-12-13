## pslpython

A Python interface to the PSL SRL framework.

Instead of trying to fit a Python project into Maven conventions,
this package is generally formatted as a standard Python package.
However, special executions have been added to the following phases:
 - clean
    - `rm -r build dist pslpython.egg-info`
 - package
    - `python3 setup.py bdist_wheel`
 - install
    - `pip install --user --upgrade dist/pslpython-*.whl`
 - integration-test
    - `./run_tests.py`
 - deploy
    - `twine upload --repository-url https://test.pypi.org/legacy/ dist/pslpython-*.whl`
    - The `TWINE_USERNAME` and `TWINE_PASSWORD` environment variables MUST be set.

Instead of `compile` and `test`, `package` and `integration-test` are used.
This is because building the package relies on the jar from the `package` phase of psl-cli,
and the tests require the package to be built.

Also note that the star in the `install` phase means that the developer should clean before and install if versions have changed.

Because of the dependence on the parent's pom and psl-cli's jar, this package is only distributed as a wheel and not a source distribution.

Developers who are not using maven should make sure that the `psl-cli` package is built before building this package.
