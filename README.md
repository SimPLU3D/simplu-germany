Find better name for Baufenster 

Group of parcels in same Baufenster: specify whether buildings must touch or keep a minimum distance
- BAUWEISE: 
    - g => buildings have to touch
    - otherwise
- building law for the area:
    - min(3m, height/2)? 

Automatize construction of property units - splitting into simulation areas by plan part 

Roof shapes
- if gable roof => modify FSI computation
- export building with roof
- ridge direction

How to differentiate between exact and maximum floors allowed


export MAVEN_OPTS=-Djava.library.path=../simplu3D/lib/native_libraries/linux-amd64
mvn compile exec:java
