const itowns = require('itowns');
import { CRS } from '@itowns/geographic';
import * as THREE from 'three';
import "./style.css";

// Retrieve the view container
const viewerDiv = document.createElement('div');
viewerDiv.id = "viewerDiv";
document.body.appendChild(viewerDiv);
// const viewerDiv = document.getElementById('viewerDiv');

console.log(itowns.proj4);

// Define the view geographic extent
CRS.defs("EPSG:4978","+proj=geocent +datum=WGS84 +units=m +no_defs +type=crs");
var cameraCoord = new itowns.Coordinates('EPSG:4326', 11.52434809, 48.1843149);

// Define the camera initial placement
const placement = {
    coord: cameraCoord,
    tilt: 20,
    range: 300,
};

// Create the view
const view = new itowns.GlobeView(viewerDiv, placement);

// Create the dem ElevationLayer and add it to the view
function addElevationLayerFromConfig(config) {
    config.source = new itowns.WMTSSource(config.source);
    view.addLayer(new itowns.ElevationLayer(config.id, config));
}
itowns.Fetcher.json('https://raw.githubusercontent.com/iTowns/itowns/refs/heads/master/examples/layers/JSONLayers/WORLD_DTM.json').then(addElevationLayerFromConfig);

// Add osm map
var orthoSource = new itowns.TMSSource({
    crs: "EPSG:3857",
    isInverted: true,
    format: "image/png",
    url: "https://maps.pole-emploi.fr/styles/klokantech-basic/${z}/${x}/${y}.png",
    attribution: {
        name: "OpenStreetMap",
        url: "http://www.openstreetmap.org/"
    },
    tileMatrixSet: "PM"
});
var orthoLayer = new itowns.ColorLayer('Ortho', { source: orthoSource, });
view.addLayer(orthoLayer);

const buildingsSource = new itowns.OGC3DTilesSource({ url: 'buildings/tileset.json' });
const buildingsLayer = new itowns.OGC3DTilesLayer('buildings', { source: buildingsSource, });
view.addLayer(buildingsLayer);

const directionalLight = new THREE.DirectionalLight(0xffffff, 1);
directionalLight.position.set(-0.9, 0.3, 1);
directionalLight.updateMatrixWorld();
view.scene.add(directionalLight);
const directionalLight2 = new THREE.DirectionalLight(0xffffff, 1);
directionalLight2.position.set(0.9, 0.3, 1);
directionalLight2.updateMatrixWorld();
view.scene.add(directionalLight2);

const ambientLight = new THREE.AmbientLight(0xffffff, 1);
view.scene.add(ambientLight);
