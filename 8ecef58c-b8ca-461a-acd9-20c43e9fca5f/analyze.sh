#!/bin/bash
analyze_result="analyze.csv"
parameter_file="parameter_file.csv"
model_output="model_output_out.csv"
max_order=$(<max_order.txt)
salib analyze sobol -p $parameter_file -Y $model_output -c 0 --max-order=$max_order -r 1000 >$analyze_result || true