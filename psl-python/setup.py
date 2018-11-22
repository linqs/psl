import os
import setuptools
import xml.etree.ElementTree

with open('README.md', 'r') as fh:
    long_description = fh.read()

# Get information from the parent POM.
pom = xml.etree.ElementTree.parse(os.path.join('..', 'pom.xml'))
url = pom.find('{http://maven.apache.org/POM/4.0.0}url').text
author = pom.find('{http://maven.apache.org/POM/4.0.0}developers/{http://maven.apache.org/POM/4.0.0}developer/{http://maven.apache.org/POM/4.0.0}name').text
email = pom.find('{http://maven.apache.org/POM/4.0.0}developers/{http://maven.apache.org/POM/4.0.0}developer/{http://maven.apache.org/POM/4.0.0}email').text

# The version requires some normalization for PEP style.
version = pom.find('{http://maven.apache.org/POM/4.0.0}version').text
version = version.lower().replace('-snapshot', '.dev0')

setuptools.setup(
    name = 'pslpython',
    version = version,

    author = author,
    author_email = email,

    description = 'A python inferface to the PSL SRL/ML software.',
    long_description = long_description,
    long_description_content_type = 'text/markdown',

    url = url,
    packages = setuptools.find_packages(),
    classifiers = [
        'Intended Audience :: Science/Research',
        'Intended Audience :: Developers',
        'License :: OSI Approved :: Apache Software License',
        'Operating System :: OS Independent',
        'Programming Language :: Python :: 3',
    ],
)
