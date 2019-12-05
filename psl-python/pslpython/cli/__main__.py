import shutil
import sys

from pslpython import model
from pslpython import util

def main(argv):
    command = [
        shutil.which('java'),
        '-jar',
        model.Model.CLI_JAR_PATH,
    ]
    command += argv

    exit_status = util.execute(command)
    if (exit_status != 0):
        sys.exit(1)

    sys.exit(0)

if (__name__ == '__main__'):
    main(sys.argv[1:])
