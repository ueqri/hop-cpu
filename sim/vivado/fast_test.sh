#!/bin/bash

set -e

PROJECT_PATH=$(realpath $(dirname $0)/..)

echo PROJECT_PATH:$PROJECT_PATH

pushd $PROJECT_PATH

sbt --client run
make -C $PROJECT_PATH/build -j
$PROJECT_PATH/build/func_test_sim 2> /dev/null

popd