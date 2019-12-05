import os
import re
import setuptools
import shutil
import xml.etree.ElementTree

BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__)))
SNAPSHOT_SUFFIX = 'rc0.dev0'

def get_description():
    with open('PYPI_README.md', 'r') as file:
        return file.read()

# Get information from the parent POM.
def get_pom_info():
    pom = xml.etree.ElementTree.parse(os.path.join(BASE_DIR, '..', 'pom.xml'))

    url = pom.find('{http://maven.apache.org/POM/4.0.0}url').text
    author = pom.find('{http://maven.apache.org/POM/4.0.0}developers/{http://maven.apache.org/POM/4.0.0}developer/{http://maven.apache.org/POM/4.0.0}name').text
    email = pom.find('{http://maven.apache.org/POM/4.0.0}developers/{http://maven.apache.org/POM/4.0.0}developer/{http://maven.apache.org/POM/4.0.0}email').text
    version = pom.find('{http://maven.apache.org/POM/4.0.0}version').text

    return (url, author, email, version)

# The build depends on having the psl-cli project built.
# Fetch the jar from there.
def copy_cli_jar(version):
    jar_path = os.path.abspath(os.path.join(BASE_DIR, '..', 'psl-cli', 'target', "psl-cli-%s.jar" % (version)))
    dest_path = os.path.join('pslpython', 'cli', 'psl-cli.jar')

    if (not os.path.isfile(jar_path)):
        raise FileNotFoundError("Could not locate psl-cli jar file (%s). The psl-cli project should be built prior this project." % (jar_path))

    shutil.copyfile(jar_path, dest_path)

def main():
    url, author, email, raw_version = get_pom_info()

    # The version requires some normalization for PEP style.
    version = raw_version.lower()
    # Snapshots get a strange-looking name and shouldn't be uploaded.
    version = version.replace('-snapshot', SNAPSHOT_SUFFIX)
    # Canaries get a dev version: <major>.<minor>.0.dev<canary>
    version = re.sub(r'^canary-(\d+\.\d+)\.(\d+)$', r'\1.0.dev\2', version)

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
            'pandas', 'pyyaml'
        ],

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
