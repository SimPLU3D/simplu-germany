**1. Find better name for Baufenster**

**2. Group of parcels in same Baufenster: specify whether buildings must touch or keep a minimum distance**
- [x] finish one and double sided cuboid objects for attached building style
- [x] adapt the moves for these cuboids
- [x] compile reference lines
- [x] instanciate building for each case
- BAUWEISE: 
- [x] g => buildings have to touch
- [ ] otherwise
    - building law for the area:
        - min(3m, height/2)? 
    - minimum distance defined in state building law: building height (without roof) * 0.4, though at least 3m. if the roof slope is below 70° its height counts for 1/3, if the slope is above 70° its full height is added to the building height. but, baufenster trumps minimum distances! (declared in zoning plan)

**3. Automatize construction of property units - splitting into simulation areas by plan part**

**4. Roof shapes**

- [x] if gable roof => modify FSI computation. roof slope is written in the legend to the plan. f ex 30-40°
- [x] export building with roof
- [x] ridge direction (not specified in plan) => we take the largest building in the fenster as a reference for the roofs orientation
- [x] roofs count in the FAR calculation where a floor is defined as an inhabitable floor above the ground with a minimum height of 2.3m

**5. How to differentiate between exact and maximum floors allowed**

**Running the simulator**
```shell
export MAVEN_OPTS="-Djava.library.path=../simplu3D/lib/native_libraries/linux-amd64 -Djts.overlay=ng"
mvn compile exec:java
```