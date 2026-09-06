#!/bin/sh
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec java -Dfile.encoding=UTF-8 -jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" "$@"
