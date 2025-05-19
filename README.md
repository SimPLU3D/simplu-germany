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
mvn exec:java
```

**Export the results to citygml**
Be careful of the directory where the *j3dcore-ogl* library is. Here it is in the *simplu3D* directory...
```shell
export MAVEN_OPTS="-Djava.library.path=../simplu3D/lib/native_libraries/linux-amd64 -Djts.overlay=ng"
mvn exec:java@export
```

**Export the results to 3DTiles**
We need to first import the data to 3dcitydb (v4 since v5 does not work with the exporter we want).
```shell
docker run -d --name citydb -p 5432:5432 -e "POSTGRES_PASSWORD=changeMe" -e "SRID=25832" 3dcitydb/3dcitydb-pg:15-3.3-4.4-alpine
```
Then we import our data.
```shell
docker run -i -t --rm --name impexp --network host -v ./output:/data 3dcitydb/impexp:latest-alpine import -H localhost -d postgres -u postgres -p changeMe /data/city_union_out_3d.gml
```
Finally, we export with py3dtilers.
We will use a modified version of https://github.com/VCityTeam/py3dtilers-docker because of an update in a dependency.
```shell
cd py3dtilers-docker
docker build -t vcity/py3dtilers  .
docker run --rm  -u $(id -u):$(id -g) --network host -v ./output:/data/ -t vcity/py3dtilers citygml-tiler --db_config_path /data/Config.yml -o /data/buildings --crs_in EPSG:25832 --crs_out EPSG:4978 --type building --split_surfaces --add_color
```
The results can be found in *./output/buildings*.

Clean up after yourself: the *impexp* and *py3dtilers* containers were already removed automatically (--rm parameter) but the *citydb* container is still running so remember to remove it when you're done.
```shell
docker container rm citydb
```
