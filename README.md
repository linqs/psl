PSL
===

### Master
[![Build Status](https://travis-ci.org/linqs/psl.svg?branch=master)](https://travis-ci.org/linqs/psl)
[![Stable Docs](https://img.shields.io/badge/docs-stable-brightgreen.svg)](https://linqs-data.soe.ucsc.edu/psl-docs/docs/psl/master-head/index.html)

### Develop
[![Build Status](https://travis-ci.org/linqs/psl.svg?branch=develop)](https://travis-ci.org/linqs/psl)
[![Develop Docs](https://img.shields.io/badge/docs-develop-orange.svg)](https://linqs-data.soe.ucsc.edu/psl-docs/docs/psl/develop-head/index.html)

Probabilistic soft logic (PSL) is a probabilistic programming language for reasoning about
relational and structured data that is designed to be highly scalable. More information about PSL
is available at the [PSL homepage](http://psl.linqs.org).

Getting Started with PSL
------------------------

If you want to use PSL to build models, you probably do not need this source code.
Instead, visit the [Getting Started guide](../../wiki/Core-Topics) to learn
how to create PSL projects that will automatically install a stable version of these libraries.

Installing PSL from Source
--------------------------

If you do want to install PSL from source, you can use [Maven](https://maven.apache.org/) 3.x.
In the top-level directory of the PSL source (which should be the same directory that holds this README), run
```
	mvn install
```

Citing PSL
----------

We hope you find PSL useful! If you have, please consider citing PSL in any related publications as
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
- [PSL homepage](http://psl.linqs.org)
- [API reference](https://linqs-data.soe.ucsc.edu/psl-docs/)
- [PSL source repository](https://github.com/linqs/psl)
- [PSL wiki](../../wiki)
- [Getting started guide](../../wiki/Core-Topics)
- [User group](https://groups.google.com/forum/#!forum/psl-users)
