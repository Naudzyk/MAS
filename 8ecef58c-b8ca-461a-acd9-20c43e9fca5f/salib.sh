#!/bin/bash
set -x
set -o xtrace
#Maximum order of indices to calculate. Choose 1 or 2, default is 2.
#Choosing 1 will reduce total model runs from N(2D + 2) to N(D + 2)
#Must use the same value (either 1 or 2) for both sampling and analysis.
samples=1024
max_order=2
solve_dir="./solve"
parameter_file="$solve_dir/parameter_file.csv"
model_input="$solve_dir/model_input.csv"
model_output="$solve_dir/model_output.csv"
analyze_result="$solve_dir/analyze.csv"
bin_dir="/f/Build/bin/win64/Release/salib.workspace"

mkdir -p $solve_dir
$bin_dir/m1.exe -i ./gas2003.sqlite3 -q "stat.txt" -p ./salib_highs.xml -o $parameter_file

# Generating samples from the command line
salib sample saltelli \
  -n $samples \
  -p $parameter_file \
  -o $model_input \
  --delimiter=' ' \
  --precision=8 \
  --max-order=$max_order
#exit
# You can also use the module directly through Python
# python -m SALib.sample.saltelli \
#      -n 1000 \
#      -p ../../src/SALib/test_functions/params/Ishigami.txt \
#      -o ../data/model_input.txt \
#      --delimiter=' ' \
#      --precision=8 \
#      --max-order=2 \
#      --seed=100

# Options:
# -p, --paramfile: Your parameter range file (3 columns: parameter name, lower bound, upper bound)
#
# -n, --samples: Sample size.
#				 Number of model runs is N(2D + 2) if calculating second-order indices (default)
#        or N(D + 2) otherwise.
#
# -o, --output: File to output your samples into.
#
# --delimiter (optional): Output file delimiter.
#
# --precision (optional): Digits of precision in the output file. Default is 8.
#
# --max-order (optional): Maximum order of indices to calculate. Choose 1 or 2, default is 2.
#								   Choosing 1 will reduce total model runs from N(2D + 2) to N(D + 2)
#								   Must use the same value (either 1 or 2) for both sampling and analysis.
#
# -s, --seed (optional): Seed value for random number generation

# Run the model using the inputs sampled above, and save outputs
$bin_dir/m2.exe -w 1 -i ./gas2003.sqlite3 -q "stat.txt" -p ./salib_highs.xml -j $model_input -d ./duckdb.json -a ./gas2003.duckdb
$bin_dir/m3.exe -d ./duckdb.json -a ./gas2003.duckdb -o $model_output -b "metric1" -c "measure0" -q "stat.txt"
sed -i '1d' $model_output

# Then use the output to run the analysis.
# Sensitivity indices will print to command line. Use ">" to write to file.

salib analyze sobol \
  -p $parameter_file \
  -Y $model_output \
  -c 0 \
  --max-order=$max_order \
  -r 1000 >$analyze_result

# Options:
# -p, --paramfile: Your parameter range file (3 columns: parameter name, lower bound, upper bound)
#
# -Y, --model-output-file: File of model output values to analyze
#
# -c, --column (optional): Column of model output file to analyze.
#                If the file only has one column, this argument will be ignored.
#
# --delimiter (optional): Model output file delimiter.
#
# --max-order (optional): Maximum order of indices to calculate.
#               This must match the value chosen during sampling.
#
# -r, --resamples (optional): Number of bootstrap resamples used to calculate confidence intervals on indices. Default 1000.
#
#
# -s, --seed (optional): Seed value for random number generation
#
# --parallel (optional): Flag to enable parallel execution with multiprocessing
#
# --processors (optional, int): Number of processors to be used with the parallel option

# salib analyze morris \
#   -p $parameter_file \
#   -Y $model_output \
#   -c 0 \
#   -X $model_input \
#   -r 1000 \
#   -l=10 \
#   --seed=100 >$analyze_result

# Options:
# -p, --paramfile: Your parameter range file
#                  (3 columns: parameter name,
#                              lower bound,
#                              upper bound)
#
# -Y, --model-output-file: File of model output values to analyze
#
# -c, --column (optional): Column of model output file to analyze.
#                If the file only has one column, this argument will be ignored.
#
# --delimiter (optional): Model output file delimiter.
#
# -X, --model-input-file: File of model input values (parameter samples).
#
# -r, --resamples (optional): Number of bootstrap resamples used to calculate confidence
#                             intervals on indices. Default 1000.
#
# -s, --seed (optional): Seed value for random number generation
