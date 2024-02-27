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

JAVA_OPEN=~/.sdkman/candidates/java/21.0.2-open/bin/java
JAVA_GRAAL=~/.sdkman/candidates/java/21.0.2-graal/bin/java
NATIVE_IMAGE=~/.sdkman/candidates/java/21.0.2-graal/bin/native-image
NATIVE_MARCH=native

JAVA=$JAVA_OPEN
JAVA_CP="-cp build/libs/1brc-walkthrough-1.0-SNAPSHOT.jar"

TASKSET="taskset -c 0-7"
HYPERFINE=hyperfine
HYPERFINE_OPTS="--warmup 3 --runs 10 $HYPERFINE_EXTRA_OPTS"


