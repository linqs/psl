PSL
===

### Build Status
[![Master](https://github.com/linqs/psl/actions/workflows/build-test.yml/badge.svg?branch=master)](https://github.com/linqs/psl/actions/workflows/build-test.yml)
[![Develop](https://github.com/linqs/psl/actions/workflows/build-test.yml/badge.svg?branch=develop)](https://github.com/linqs/psl/actions/workflows/build-test.yml)

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
