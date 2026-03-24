#!/bin/bash
set -x
set -o xtrace
model_input="model_input.csv"
cp gas2003_in.duckdb gas2003_out.duckdb
./m2 -w 1 -i ./gas2003.sqlite3 -q "stat2.txt" -p ./salib_highs.xml -j $model_input -d ./duckdb.json -a ./gas2003_out.duckdb