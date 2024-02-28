# 1brc-walkthrough
The step-by-step walkthrough over 1brc challenge.

## Prerequisites
Install Open JDK and GraalVM JDK using sdkman:
``` bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.2-open
sdk install java 21.0.2-graal
```

Install toolchain for GraalVM native-image: https://www.graalvm.org/22.0/reference-manual/native-image/#prerequisites.

Install hyperfine: https://github.com/sharkdp/hyperfine.

# Run
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

## AWS c7a.4xlarge
* CPU: AMD EPYC 9R14
* MEM: DDR5 4800 MT/s 32 GB
* AFFINITY: 0-7
  
| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 145.638 ± 0.676 |             0.0 | 280.868 ± 9.476 |             0.0 | 
| 01 | NoGarbage          |  45.830 ± 2.907 |          -68.53 |  63.655 ± 0.195 |          -77.34 |
| 02 | DirectBuffer       |  42.907 ± 3.610 |           -6.38 |  63.838 ± 1.840 |            0.29 |
| 03 | MappedSegment      |  40.230 ± 0.415 |           -6.24 |  70.228 ± 5.407 |           10.01 |
| 04 | Parallelism        |   5.697 ± 0.578 |          -85.84 |   9.285 ± 0.876 |          -86.78 |
| 05 | HashWhileParsing   |   5.443 ± 0.703 |           -4.46 |   8.668 ± 1.144 |           -6.65 |
| 06 | SimpleMap          |   5.064 ± 0.353 |           -6.96 |   7.769 ± 0.824 |          -10.37 |
| 07 | BranchyTemperature |   4.284 ± 0.182 |          -15.41 |   6.527 ± 0.158 |          -15.98 |
| 08 | UnsafeParsing      |   3.892 ± 0.096 |           -9.14 |   5.960 ± 0.357 |           -8.70 |
| 09 | NoKeyCopy          |   3.371 ± 0.059 |          -13.40 |   6.717 ± 0.523 |           12.70 |
| 10 | SwarStation (*)    |   2.093 ± 0.092 |          -37.90 |   6.961 ± 0.096 |            3.64 |
| 11 | SwarTemperature    |   1.846 ± 0.036 |          -11.83 |   6.700 ± 0.094 |           -3.75 |
| 12 | BetterHash         |   1.844 ± 0.076 |           -0.10 |   2.727 ± 0.112 |          -59.30 |
| 13 | BetterMap          |   1.782 ± 0.043 |           -3.37 |   2.152 ± 0.025 |          -21.11 |
| 14 | ParallelismSharing |   1.766 ± 0.018 |           -0.86 |   2.200 ± 0.030 |            2.27 |
| 15 | ParallelismMerging |   1.763 ± 0.008 |           -0.21 |   2.202 ± 0.024 |            0.09 |
| 16 | Graal JIT          |   1.742 ± 0.020 |           -1.15 |   2.170 ± 0.025 |           -1.49 |
| 17 | Graal AOT          |   1.468 ± 0.004 |          -15.76 |   1.842 ± 0.002 |          -15.08 |
| 18 | BranchyMinMax      |   1.449 ± 0.011 |           -1.30 |   1.802 ± 0.001 |           -2.20 |
| 19 | Branchy08Loop      |   1.396 ± 0.003 |           -3.61 |   1.844 ± 0.002 |            2.37 |
| 20 | Branchy16Loop      |   1.349 ± 0.003 |           -3.37 |   1.964 ± 0.002 |            6.49 |
| 21 | CMOV               |   1.261 ± 0.004 |           -6.52 |   1.899 ± 0.001 |           -3.34 |
| 22 | ILP                |   0.991 ± 0.003 |          -21.46 |   1.757 ± 0.001 |           -7.46 |
| 23 | Subprocess         |   0.862 ± 0.003 |          -13.01 |   1.596 ± 0.001 |           -9.14 |
| 97 | Original           |   0.854 ± 0.004 |           -0.95 |   1.588 ± 0.002 |           -0.53 |
| 98 | Original - Sharing |   0.933 ± 0.007 |            9.25 |   1.716 ± 0.023 |            8.03 |
| 99 | Original + CMOV    |   0.860 ± 0.002 |            0.74 |   1.611 ± 0.001 |            1.43 |

* (*) hash function is affected, improving SwarStation change for 413 dramatically and worsening 10k a bit.

## MacBook Pro
* CPU: Apple M1 Pro
* MEM: 32 GB
* AFFINITY: 0-9

| #  | Change             |      Time (413) | Reduction (413) |      Time (10k) | Reduction (10k) |
|----|--------------------|----------------:|----------------:|----------------:|----------------:|
| 00 | Baseline           | 189.903 ± 2.454 |             0.0 | 270.058 ± 2.582 |             0.0 |
| 01 | NoGarbage          |  35.674 ± 0.105 |          -81.21 |  68.160 ± 5.800 |          -74.76 |
| 02 | DirectBuffer       |  31.973 ± 0.060 |          -10.37 |  50.690 ± 0.944 |          -25.63 |
| 03 | MappedSegment      |  32.329 ± 0.937 |            1.11 |  63.941 ± 9.198 |           26.14 |
| 04 | Parallelism        |   4.560 ± 0.476 |          -85.89 |   8.527 ± 0.611 |          -86.66 |
| 05 | HashWhileParsing   |   4.641 ± 0.834 |            1.78 |   7.596 ± 0.500 |          -10.93 |
| 06 | SimpleMap          |   3.782 ± 0.128 |          -18.52 |   5.928 ± 0.480 |          -21.95 |
| 07 | BranchyTemperature |   3.453 ± 0.048 |           -8.70 |   6.980 ± 0.187 |           17.73 |
| 08 | UnsafeParsing      |   3.073 ± 0.027 |          -11.01 |   4.918 ± 0.390 |          -29.53 |
| 09 | NoKeyCopy          |   2.573 ± 0.065 |          -16.27 |   4.311 ± 0.047 |          -12.36 |
| 10 | SwarStation (*)    |   1.899 ± 0.022 |          -26.20 |   9.243 ± 0.160 |          114.42 |
| 11 | SwarTemperature    |   2.067 ± 0.036 |            8.83 |   9.160 ± 0.091 |           -0.90 |
| 12 | BetterHash         |   2.010 ± 0.046 |           -2.72 |   2.574 ± 0.053 |          -71.90 |
| 13 | BetterMap          |   1.798 ± 0.022 |          -10.58 |   2.159 ± 0.057 |          -16.11 |
| 14 | ParallelismSharing |   1.874 ± 0.022 |            4.27 |   2.244 ± 0.090 |            3.92 |
| 15 | ParallelismMerging |   1.879 ± 0.023 |            0.22 |   2.233 ± 0.080 |           -0.52 |
| 16 | Graal JIT          |   1.839 ± 0.043 |           -2.12 |   2.135 ± 0.069 |           -4.37 |
| 17 | Graal AOT          |   1.633 ± 0.009 |          -11.19 |   1.926 ± 0.031 |           -9.77 |
| 18 | BranchyMinMax      |   1.637 ± 0.009 |            0.23 |   1.908 ± 0.014 |           -0.96 |
| 19 | Branchy08Loop      |   1.586 ± 0.007 |           -3.13 |   1.919 ± 0.034 |            0.60 |
| 20 | Branchy16Loop      |   1.600 ± 0.023 |            0.92 |   2.150 ± 0.034 |           12.02 |
| 21 | CMOV               |   1.464 ± 0.046 |           -8.49 |   2.013 ± 0.015 |           -6.36 |
| 22 | ILP                |   0.910 ± 0.018 |          -37.82 |   1.628 ± 0.030 |          -19.14 |
| 23 | Subprocess         |   0.907 ± 0.021 |           -0.36 |   1.596 ± 0.020 |           -1.96 |
| 97 | Original           |   0.978 ± 0.035 |            7.78 |   1.647 ± 0.014 |            3.22 |
| 98 | Original - Sharing |   1.008 ± 0.039 |            3.14 |   1.728 ± 0.026 |            4.87 |
| 99 | Original + CMOV    |   0.870 ± 0.009 |          -10.98 |   1.578 ± 0.018 |           -4.21 |

* (*) hash function is affected, improving SwarStation change for 413 and worsening 10k a bit dramatically.
