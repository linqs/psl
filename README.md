PSL
===

### Build Status
[![Main](https://github.com/linqs/psl/actions/workflows/main.yml/badge.svg)](https://github.com/linqs/psl/actions/workflows/main.yml)

Probabilistic soft logic (PSL) is a machine learning framework for developing probabilistic models.
PSL models are easy to use and fast.
You can define models using a straightforward logical syntax and solve them with fast convex optimization.
PSL has produced state-of-the-art results in many areas spanning natural language processing, social-network analysis, knowledge graphs, recommender system, and computational biology.
More information about PSL is available at the [PSL homepage](https://psl.linqs.org).

Getting Started with PSL
------------------------

If you want to use PSL to build models, you probably do not need this source code.
Instead, visit the [Getting Started guide](https://psl.linqs.org/blog/2018/07/15/getting-started-with-psl.html) to learn how to create PSL projects that will automatically install a stable version of these libraries.

Installing PSL from Source
--------------------------

If you do want to install PSL from source, you can use [Maven](https://maven.apache.org/) 3.x.
In the top-level directory of the PSL source (which should be the same directory that holds this README), run:
```sh
mvn install
```

Installing PSL with Gurobi
--------------------------

PSL can additionally be used with the [Gurobi](http://www.gurobi.com/) solver for inference.
Gurobi is a commercial solver, but free academic licenses are available.
To use Gurobi with PSL, you must have Gurobi installed and licensed, see [Gurobi Quickstart Guide](https://www.gurobi.com/documentation/quickstart.html).
Further, you must install the Gurobi jar file into your local Maven repository.
See [Guide to installing 3rd party JARs](https://maven.apache.org/guides/mini/guide-3rd-party-jars-local.html) for more information.

To do this, first download the Gurobi jar file from the [Gurobi website](https://www.gurobi.com/downloads/).
You will need to create an account and agree to the license terms.
You must also obtain a [Gurobi license](https://www.gurobi.com/documentation/current/quickstart_windows/obtaining_a_grb_license.html) that is registered and saved to your machine.
Be sure to export the `GUROBI_HOME` environment variable to point to your install directory, `<installdir>`, and `GRB_LICENSE_FILE` environment variable to point to the location of the license file.
Moreover, you must have the Gurobi install bin directory, `<installdir>/bin`, added to your `PATH` environment variable and `<installdir>/lib` added to your `LD_LIBRARY_PATH` environment variable.
Then, run the following command, replacing `<installdir>/lib/gurobi.jar` with the path to the downloaded jar file and `<version>` with the version of Gurobi you downloaded:
```sh
mvn install:install-file -Dfile=<installdir>/lib/gurobi.jar -DgroupId=com.gurobi -DartifactId=gurobi -Dversion=<version> -Dpackaging=jar
```
If you are using a version of Gurobi other than 10.0.3, you will also need to update the Gurobi dependency version in the PSL `pom.xml` file.
Then, you can install PSL with Gurobi support by running:
```sh
mvn install -P Gurobi
```
PSL inference can then be run with Gurobi using the `GurobiInference` class.

Citing PSL
----------

We hope you find PSL useful!
If you have, please consider citing PSL in any related publications as
```
@article{bach:jmlr17,
  Author = {Bach, Stephen H. and Broecheler, Matthias and Huang, Bert and Getoor, Lise},
  Journal = {Journal of Machine Learning Research (JMLR)},
  Title = {Hinge-Loss {M}arkov Random Fields and Probabilistic Soft Logic},
  Year = {2017}
}
```

Additional Resources
====================
- [PSL Homepage](https://psl.linqs.org)
- [PSL Examples](https://github.com/linqs/psl-examples)
- [API Reference](https://psl.linqs.org/api/)
- [PSL Source Repository](https://github.com/linqs/psl)
- [PSL Wiki](https://psl.linqs.org/wiki/)
- [Getting Started Guide](https://psl.linqs.org/blog/2018/07/15/getting-started-with-psl.html)
- [User Group](https://groups.google.com/forum/#!forum/psl-users)
