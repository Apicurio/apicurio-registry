# Apicurio Performance Testing

## Running the Perf Test

    mvn gatling:test -Dgatling.simulationClass=simulations.BasicSimulation

Possible values for the simulation class:

* BasicSimulation
* PersistenceSimulation
* InitialLoadSimulation
* FetchByIdSimulation
