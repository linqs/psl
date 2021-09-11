## pslpython

A Python interface to the PSL SRL framework.

Instead of trying to fit a Python project into Maven conventions,
this package is generally formatted as a standard Python package.
However, special executions have been added to the following phases:
 - clean
    - `./build/clean.sh`
    - Removes artifacts created by the build and package steps.
 - package
    - `./build/build.sh`
    - Creates a wheel in `dist`.
 - install
    - `./build/install.sh`
    - Installs this package into the user's local pip directory.
 - integration-test
    - `./run_tests.py`
 - deploy
    - `./build/deploy.sh`
    - Uses twine to upload the build artifacts (from the package step) to PyPi (test or release server).
    - The `TWINE_USERNAME` and `TWINE_PASSWORD` environment variables MUST be set.

Instead of `compile` and `test`, `package` and `integration-test` are used.
This is because building the package relies on the jar from the `package` phase of psl-cli,
and the tests require the package to be built.

Notes:
- The star in the `install` phase means that the developer should clean before and install if versions have changed.
- Because of the dependence on the parent's pom and psl-cli's jar, this package is only distributed as a wheel and not a source distribution.
- Developers who are not using maven should make sure that the `psl-cli` package is built before building this package.
- There is an optional dependency on tensorflow>=2.4.0 to use neural functionality.
