##############################################################################
# makefile
#
# Author:           Hyunwoo Oh, Computer Architecture Lab., SEOULTECH
# Created:          01/20/24 
# Description:      simple makefile for verilator simulation
##############################################################################


CONFIG_FILE = config.vlt
OPTIONS = --cc --exe -x-assign fast -Wall -j 0 \
		  --timescale 1ns/1ns \
		  --threads 1 \
		  --trace \
#		  --assert \
		  --coverage

default: all
all: sim_main.cpp makefile
	@echo 
	@echo "-- Verilator simulation for DL-Sort"
	
	@echo "-- VERILATE ----------------"
	verilator $(OPTIONS) DLSorter.sv $(CONFIG_FILE) sim_main.cpp

	@echo
	@echo "-- BUILD -------------------"
	@make -j -C obj_dir -f VDLSorter.mk

	@echo
	@echo "-- RUN ---------------------"
	@rm -rf logs
	@mkdir -p logs
	@numactl obj_dir/VDLSorter +trace

	@echo
	@echo "-- DONE --------------------"
	@echo "Open log/wave.vcd to visualize the waveform"
	@echo

clean:
	rm -rf obj_dir logs *.log *.dmp *.vpd coverage.dat core
