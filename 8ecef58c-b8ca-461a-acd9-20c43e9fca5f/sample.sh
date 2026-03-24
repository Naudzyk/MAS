#!/bin/bash
set -x
set -o xtrace
samples=$(<samples.txt)
max_order=$(<max_order.txt)
salib sample saltelli -n $samples -p parameter_file.csv -o model_input.csv --delimiter=' ' --precision=8 --max-order=$max_order