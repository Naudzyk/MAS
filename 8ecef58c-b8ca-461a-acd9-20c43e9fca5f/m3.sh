#!/bin/bash
set -x
set -o xtrace
model_output="model_output_out.csv"
./m3 -d ./duckdb.json -a ./gas2003_out.duckdb -o $model_output -b "metric1" -c "measure0" -q "stat3.txt"
sed -i '1d' $model_output