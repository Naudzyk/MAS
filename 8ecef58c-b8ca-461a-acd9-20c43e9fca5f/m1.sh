#!/bin/bash
set -x
set -o xtrace
./m1 -i gas2003.sqlite3 -q "stat1.txt" -p salib_highs.xml -o parameter_file.csv