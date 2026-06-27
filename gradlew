#!/bin/sh
#
# Gradle start up script for UN*X
#

# Attempt to set APP_HOME
PRG="$0"
while [ -h "$PRG" ] ; do
    ls=$(ls -ld "$PRG")
    link=$(expr "$ls" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=$(dirname "$PRG")"/$link"
    fi
done
SAVED="$(pwd)"
cd "$(dirname \"$PRG\")/" >/dev/null
APP_HOME="$(pwd -P)"
cd "$SAVED" >/dev/null

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

GRADLE_OPTS="$GRADLE_OPTS \"-Xdock:name=$APP_NAME\" \"-Xdock:icon=$APP_HOME/media/gradle.icns\""

MAX_FD="maximum"

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
    echo
    exit 1
}

OS=$(uname -s)
case "$OS" in
    CYGWIN* )
        cygwin=true
        ;;
    Darwin* )
        darwin=true
        ;;
    MSYS* | MINGW* )
        msys=true
        ;;
esac

if [ "$cygwin" = "true" -o "$msys" = "true" ] ; then
    APP_HOME=$(cygpath --path --mixed "$APP_HOME")
fi

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec "$JAVACMD" "${JVM_OPTS[@]}" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
