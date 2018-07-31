PSL
===

### Master
[![Build Status](https://travis-ci.org/linqs/psl.svg?branch=master)](https://travis-ci.org/linqs/psl)
[![Stable Docs](https://img.shields.io/badge/docs-stable-brightgreen.svg)](https://linqs-data.soe.ucsc.edu/psl-docs/docs/psl/master-head/index.html)

### Develop
[![Build Status](https://travis-ci.org/eriq-augustine/psl.svg?branch=develop)](https://travis-ci.org/eriq-augustine/psl)
[![Develop Docs](https://img.shields.io/badge/docs-develop-orange.svg)](https://linqs-data.soe.ucsc.edu/psl-docs/docs/psl/develop-head/index.html)

Probabilistic soft logic (PSL) is a machine learning framework for developing probabilistic models.
PSL models are easy to use and fast.
You can define models using a straightforward logical syntax and solve them with fast convex optimization.
PSL has produced state-of-the-art results in many areas spanning natural language processing, social-network analysis, knowledge graphs, recommender system, and computational biology.
More information about PSL is available at the [PSL homepage](http://psl.linqs.org).

Getting Started with PSL
------------------------

If you want to use PSL to build models, you probably do not need this source code.
Instead, visit the [Getting Started guide](../../wiki/Core-Topics) to learn how to create PSL projects that will automatically install a stable version of these libraries.

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
- [PSL Homepage](http://psl.linqs.org)
- [PSL Examples](https://github.com/linqs/psl-examples)
- [API Reference](https://linqs-data.soe.ucsc.edu/psl-docs/)
- [PSL Source Repository](https://github.com/linqs/psl)
- [PSL Development Repository](https://github.com/eriq-augustine/psl)
- [PSL Utils Repository](https://github.com/linqs/psl-utils)
- [PSL Experimental Repository](https://github.com/linqs/psl-experimental)
- [PSL Wiki](../../wiki)
- [Getting Started Guide](../../wiki/Using-the-CLI)
- [User Group](https://groups.google.com/forum/#!forum/psl-users)
