#!/bin/bash
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

NAME=10k
FILE=measurements.10k.1B.txt

rm -r results/$NAME/
mkdir -p results/$NAME/

rm measurements.txt
ln -s $FILE measurements.txt

for i in {0..24}
do
  number=$(printf "%02d" $i)
  export HYPERFINE_EXTRA_OPTS="--export-json results/$NAME/$number.json"
  echo "Evaluating #$number"
  ./eval.sh ./run-$number.sh
done

for i in {97..99}
do
  number=$i
  export HYPERFINE_EXTRA_OPTS="--export-json results/$NAME/$number.json"
  echo "Evaluating #$number"
  ./eval.sh ./run-$number.sh
done