#!/bin/sh
#
#  Copyright 2023 The original authors
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

. ./env.sh

CLASS_NAME=dev.akorzun.onebrc.Challenge_20_Branchy16Loop
IMAGE_NAME=build/image-20

if ! [ -f $IMAGE_NAME ]; then
    NATIVE_IMAGE_OPTS="-H:+UnlockExperimentalVMOptions -H:TuneInlinerExploration=1 -H:-GenLoopSafepoints --gc=epsilon -O3 -march=$NATIVE_MARCH -R:MaxHeapSize=64m --enable-preview --initialize-at-build-time=$CLASS_NAME"
    $NATIVE_IMAGE $NATIVE_IMAGE_OPTS $JAVA_CP -o $IMAGE_NAME $CLASS_NAME
fi

$TASKSET $HYPERFINE "$IMAGE_NAME -i $1" $HYPERFINE_OPTS