# cellsim
A simulation of monocellular cells in a petri dish

This project consists of two modules: a cell simulation component and a dna abstraction.

## Dna Abstraction

Dna is abstracted as an array. It consists of different sections (genes and alleles), which work together with an interpreter (I call them ribosomes - let's not get too realistic for now) to create traits. There is a primitive system that enables dominant/recessive properties of alleles, as well as a degree of codominance.

## Simulation

The simulation consists of a grid to represent a petri dish, in which cells (dna payloads) will move around.

## Todo

* Simulation AI pathing
