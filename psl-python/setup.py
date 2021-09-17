import os
import re
import setuptools
import shutil
import xml.etree.ElementTree

import git

THIS_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__)))
ROOT_DIR = os.path.join(THIS_DIR, '..')
POM_PATH = os.path.join(ROOT_DIR, 'pom.xml')

def get_description():
    with open('PYPI_README.md', 'r') as file:
        return file.read()

# Get information from the parent POM.
def get_pom_info():
    pom = xml.etree.ElementTree.parse(POM_PATH)

    url = pom.find('{http://maven.apache.org/POM/4.0.0}url').text
    author = pom.find('{http://maven.apache.org/POM/4.0.0}developers/{http://maven.apache.org/POM/4.0.0}developer/{http://maven.apache.org/POM/4.0.0}name').text
    email = pom.find('{http://maven.apache.org/POM/4.0.0}developers/{http://maven.apache.org/POM/4.0.0}developer/{http://maven.apache.org/POM/4.0.0}email').text
    version = pom.find('{http://maven.apache.org/POM/4.0.0}version').text

    return (url, author, email, version)

# The build depends on having the psl-cli project built.
# Fetch the jar from there.
def copy_cli_jar(version):
    jar_path = os.path.abspath(os.path.join(THIS_DIR, '..', 'psl-cli', 'target', "psl-cli-%s.jar" % (version)))
    dest_path = os.path.join('pslpython', 'cli', 'psl-cli.jar')

    if (not os.path.isfile(jar_path)):
        raise FileNotFoundError("Could not locate psl-cli jar file (%s). The psl-cli project should be built prior this project." % (jar_path))

    shutil.copyfile(jar_path, dest_path)

# Get the distance to the last tag that looks like a version.
def find_tag_distance():
    repo = git.Repo(ROOT_DIR)

    highest_version = None

    for tag in repo.tags:
        match = re.match(r'^(\d+)\.(\d+)\.(\d+)$', str(tag))
        if (match is None):
            continue

        version = tuple(map(int, match.group(1, 2, 3)))
        if (highest_version is None or version > highest_version):
            highest_version = version

    highest_version = '.'.join(map(str, highest_version))
    commits = repo.git.log('--format=%H', "HEAD...%s" % (highest_version))

    return len(commits.splitlines())

def create_version(raw_version):
    # The version requires some normalization for PEP style.
    version = raw_version.lower()

    match = re.match(r'^canary-(\d+)\.(\d+)\.(\d+)$', version)
    if (match is not None):
        major, minor, patch = map(int, match.group(1, 2, 3))
        return "%d.%d.0.rc%d" % (major, minor, patch)

    match = re.match(r'^(\d+)\.(\d+)\.(\d+)-snapshot$', version)
    if (match is not None):
        major, minor, patch = map(int, match.group(1, 2, 3))

        # Number of commits since the last release.
        version_distance = find_tag_distance()

        return "%d.%d.%d.dev%d" % (major, minor, patch, version_distance)

    match = re.match(r'^(\d+)\.(\d+)\.(\d+)$', version)
    if (match is not None):
        major, minor, patch = map(int, match.group(1, 2, 3))
        return "%d.%d.%d" % (major, minor, patch)

    raise ValueError("Improperly formatted version: " + raw_version)

def main():
    url, author, email, raw_version = get_pom_info()

    version = create_version(raw_version)

    copy_cli_jar(raw_version)

    setuptools.setup(
        name = 'pslpython',
        version = version,
        url = url,
        keywords = 'PSL ML SRL',

        author = author,
        author_email = email,

        description = 'A python inferface to the PSL SRL/ML software.',
        long_description = get_description(),
        long_description_content_type = 'text/markdown',

        packages = setuptools.find_packages(),

        include_package_data = True,
        package_data = {
            'pslpython.cli': [
                'psl-cli.jar',
            ]
        },

        install_requires = [
            'pandas>=0.24.1', 'pyyaml>=3.13'
        ],

        extras_require = {
            'neural': ['tensorflow>=2.5.1'],
        },

        python_requires = '>=3.5',

        classifiers = [
            'Intended Audience :: Science/Research',
            'Intended Audience :: Developers',
            'License :: OSI Approved :: Apache Software License',
            'Operating System :: OS Independent',
            'Programming Language :: Python :: 3',
        ],
    )

if (__name__ == '__main__'):
    main()
