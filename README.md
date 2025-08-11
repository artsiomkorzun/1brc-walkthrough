# 1brc-walkthrough
The step-by-step walkthrough over 1brc challenge. See: https://github.com/artsiomkorzun/1brc-walkthrough/blob/main/Walkthrough.md

## Prerequisites
Install Open JDK and GraalVM JDK using sdkman:
``` bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 25.ea.33-open
sdk install java 25.ea.31-graal
```

Install toolchain for GraalVM native-image: https://www.graalvm.org/22.0/reference-manual/native-image/#prerequisites.

Install hyperfine: https://github.com/sharkdp/hyperfine.

## Run
```bash
 # tests might fail because they are run in one jvm, some solutions do not cleanup resources
./gradlew clean build -x test
 # generates 413 and 10k files
./generate-all.sh
 # evaluates 413 and 10k cases with all solutions
./eval-all.sh 
```

## Results
Results are collected using hyperfine with 3 warmups and 10 measurements. 

### AWS c7a.4xlarge (25.ea.31-graal, August 2025)
* CPU: AMD EPYC 9R14
* MEM: DDR5 4800 MT/s 32 GB
* AFFINITY: 0-7
* PROFILE: sudo tuned-adm profile hpc-compute

| #  | Change        |     Time (413) | Reduction (413) |    Time (10k) | Reduction (10k) |
|----|---------------|---------------:|----------------:|--------------:|----------------:|
| 97 | My Original   |  0.880 ± 0.001 |            0.00 | 1.711 ± 0.003 |             0.0 |
| 26 | Vectorization |  0.777 ± 0.002 |           12.58 | 1.519 ± 0.002 |           12.93 |


### AWS c7a.4xlarge (GraalVM 21.0.2, April 2024)
* CPU: AMD EPYC 9R14
* MEM: DDR5 4800 MT/s 32 GB
* AFFINITY: 0-7

| #  | Change                |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|-----------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline              | 125.650 ± 0.740 |             0.0 | 160.107 ± 1.963 |             0.0 | 
| 01 | Substring             | 104.383 ± 0.363 |          -16.93 | 132.624 ± 1.320 |          -17.17 |
| 02 | NoGarbage             |  41.824 ± 0.101 |          -59.93 |  61.337 ± 0.152 |          -53.75 |
| 03 | DirectBuffer          |  40.261 ± 0.119 |           -3.73 |  56.018 ± 0.183 |           -8.67 |
| 04 | MappedSegment         |  39.868 ± 0.338 |           -0.98 |  57.840 ± 0.297 |            3.25 |
| 05 | Parallelism           |   5.583 ± 0.378 |          -86.00 |   8.018 ± 0.385 |          -86.14 |
| 06 | HashWhileParsing      |   5.212 ± 0.466 |           -6.64 |   7.649 ± 0.933 |           -4.59 |
| 07 | SimpleMap             |   4.915 ± 0.280 |           -5.70 |   7.211 ± 0.829 |           -5.73 |
| 08 | BranchyTemperature    |   4.220 ± 0.181 |          -14.14 |   6.623 ± 0.555 |           -8.15 |
| 09 | UnsafeParsing         |   3.861 ± 0.220 |           -8.51 |   6.035 ± 0.574 |           -8.88 |
| 10 | NoKeyCopy             |   3.416 ± 0.055 |          -11.51 |   5.207 ± 0.023 |          -13.72 |
| 11 | SwarStation           |   2.059 ± 0.070 |          -39.74 |   7.042 ± 0.122 |           35.26 |
| 12 | SwarTemperature       |   1.798 ± 0.021 |          -12.67 |   6.865 ± 0.167 |           -2.52 |
| 13 | BetterHash            |   1.776 ± 0.038 |           -1.22 |   2.698 ± 0.078 |          -60.70 |
| 14 | BetterMap             |   1.727 ± 0.043 |           -2.77 |   2.179 ± 0.029 |          -19.23 |
| 15 | ParallelismSharing    |   1.728 ± 0.020 |            0.05 |   2.211 ± 0.031 |            1.47 |
| 16 | ParallelismMerging    |   1.725 ± 0.015 |           -0.13 |   2.218 ± 0.030 |            0.31 |
| 17 | Graal JIT             |   1.697 ± 0.033 |           -1.66 |   2.178 ± 0.023 |           -1.80 |
| 18 | Graal AOT             |   1.433 ± 0.002 |          -15.52 |   1.860 ± 0.001 |          -14.62 |
| 19 | BranchyMinMax         |   1.415 ± 0.002 |           -1.31 |   1.820 ± 0.002 |           -2.15 |
| 20 | Branchy08Loop         |   1.372 ± 0.002 |           -3.03 |   1.871 ± 0.003 |            2.80 |
| 21 | Branchy16Loop         |   1.332 ± 0.002 |           -2.87 |   1.995 ± 0.002 |            6.64 |
| 22 | CMOV                  |   1.246 ± 0.002 |           -6.50 |   1.931 ± 0.002 |           -3.22 |
| 23 | ILP                   |   0.964 ± 0.002 |          -22.64 |   1.788 ± 0.001 |           -7.39 |
| 24 | Subprocess            |   0.834 ± 0.001 |          -13.45 |   1.627 ± 0.003 |           -9.01 |
| 97 | My Original           |   0.835 ± 0.002 |            0.09 |   1.635 ± 0.001 |            0.49 |
| 98 | My Original + CMOV    |   0.836 ± 0.005 |            0.11 |   1.638 ± 0.001 |            0.19 |
| 99 | My Original - Sharing |   0.914 ± 0.020 |            9.48 |   1.709 ± 0.021 |            4.55 |
| -- | -                     |              -- |              -- |              -- |              -- | 
| 25 | Bonus                 |   0.802 ± 0.001 |               - |   1.656 ± 0.001 |               - |

* (*) hash function is affected, see collision table below.

### MacBook Pro (GraalVM 21.0.2, April 2024)
* CPU: Apple M1 Pro
* MEM: 32 GB
* AFFINITY: 0-9

| #  | Change                |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|-----------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline              | 192.498 ± 2.877 |             0.0 | 188.997 ± 1.997 |             0.0 |
| 01 | Substring             | 148.336 ± 1.396 |          -22.94 | 146.822 ± 0.495 |          -22.32 |
| 02 | NoGarbage             |  35.499 ± 0.272 |          -76.07 |  51.339 ± 0.216 |          -65.03 |
| 03 | DirectBuffer          |  31.722 ± 0.086 |          -10.64 |  48.257 ± 0.358 |           -6.00 |
| 04 | MappedSegment         |  32.186 ± 0.514 |            1.46 |  55.884 ± 7.042 |           15.81 |
| 05 | Parallelism           |   4.577 ± 0.647 |          -85.78 |   8.293 ± 0.568 |          -85.16 |
| 06 | HashWhileParsing      |   4.418 ± 0.764 |           -3.48 |   7.414 ± 0.465 |          -10.61 |
| 07 | SimpleMap             |   3.688 ± 0.106 |          -16.54 |   6.186 ± 0.686 |          -16.56 |
| 08 | BranchyTemperature    |   3.328 ± 0.062 |           -9.76 |   6.620 ± 0.554 |            7.01 |
| 09 | UnsafeParsing         |   2.958 ± 0.013 |          -11.10 |   4.879 ± 0.345 |          -26.29 |
| 10 | NoKeyCopy             |   2.572 ± 0.014 |          -13.06 |   4.202 ± 0.028 |          -13.89 |
| 11 | SwarStation           |   1.875 ± 0.025 |          -27.10 |   8.962 ± 0.039 |          113.30 |
| 12 | SwarTemperature       |   2.034 ± 0.037 |            8.51 |   8.871 ± 0.029 |           -1.02 |
| 13 | BetterHash            |   1.969 ± 0.048 |           -3.21 |   2.442 ± 0.017 |          -72.47 |
| 14 | BetterMap             |   1.779 ± 0.032 |           -9.65 |   2.067 ± 0.018 |          -15.36 |
| 15 | ParallelismSharing    |   1.841 ± 0.034 |            3.47 |   2.133 ± 0.024 |            3.20 |
| 16 | ParallelismMerging    |   1.821 ± 0.015 |           -1.06 |   2.147 ± 0.020 |            0.65 |
| 17 | Graal JIT             |   1.787 ± 0.013 |           -1.91 |   2.062 ± 0.060 |           -3.97 |
| 18 | Graal AOT             |   1.613 ± 0.013 |           -9.70 |   1.887 ± 0.032 |           -8.48 |
| 19 | BranchyMinMax         |   1.598 ± 0.007 |           -0.93 |   1.892 ± 0.029 |            0.26 |
| 20 | Branchy08Loop         |   1.562 ± 0.007 |           -2.25 |   1.860 ± 0.009 |           -1.70 |
| 21 | Branchy16Loop         |   1.571 ± 0.009 |            0.57 |   2.104 ± 0.016 |           13.12 |
| 22 | CMOV                  |   1.434 ± 0.013 |           -8.75 |   1.964 ± 0.017 |           -6.66 |
| 23 | ILP                   |   0.893 ± 0.003 |          -37.72 |   1.582 ± 0.015 |          -19.42 |
| 24 | Subprocess            |   0.876 ± 0.005 |           -1.89 |   1.557 ± 0.015 |           -1.58 |
| 97 | My Original           |   0.962 ± 0.017 |            9.80 |   1.612 ± 0.006 |            3.51 |
| 98 | My Original + CMOV    |   0.854 ± 0.004 |          -11.26 |   1.531 ± 0.014 |           -5.03 |
| 99 | My Original - Sharing |   0.961 ± 0.009 |           -0.07 |   1.638 ± 0.013 |            1.59 |
| -- | -                     |              -- |              -- |              -- |              -- | 
| 25 | Bonus                 |   0.776 ± 0.004 |               - |   1.494 ± 0.004 |               - |

* (*) hash function is affected, see collision table below.

## Collisions
The table shows the number of collisions when a certain change affected hash function.

| #  | Change      | Stations 413 | Stations 10k |
|----|-------------|-------------:|-------------:|
| 06 | SimpleMap   |            3 |        ~1053 | 
| 11 | SwarStation |            8 |        ~3745 | 
| 13 | BetterHash  |            1 |         ~712 |  
| 14 | BetterMap   |            0 |         ~778 |
