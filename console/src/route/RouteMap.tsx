import { useEffect, useMemo, useRef, useState } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { Button } from "../ui";
import type { Warehouse } from "../warehouse/api";
import type { PlannedRoute, Territory } from "./api";

const LEG_MS = 2800;

// Map tiles: OpenStreetMap unless the build names another source (a tile provider or our own tile
// server). nginx.conf allows the images only from MAP_TILE_ORIGIN: change both together.
const TILE_URL = import.meta.env.VITE_MAP_TILE_URL || "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
const TILE_ATTRIBUTION = import.meta.env.VITE_MAP_TILE_ATTRIBUTION
  || '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>';

type LatLon = [number, number];
type Point = { lat: number; lon: number; label: string; title: string };

function routePoints(plan: PlannedRoute): Point[] {
  const stops = [...plan.stops].sort((a, b) => a.sequenceNo - b.sequenceNo);
  return [
    { lat: plan.depotLatitude, lon: plan.depotLongitude, label: "D", title: "Depo" },
    ...stops.map((stop) => ({
      lat: stop.latitude,
      lon: stop.longitude,
      label: String(stop.sequenceNo),
      title: `${stop.sequenceNo}. ${stop.customerName}`,
    })),
  ];
}

/**
 * One line per leg (depot to the first stop, then stop to stop). The roads from OSRM when the plan
 * has them, one per leg; otherwise straight lines between the points.
 */
export function legLines(plan: PlannedRoute, points: Point[]): { legs: LatLon[][]; roads: boolean } {
  const roads = plan.roadLegs ?? [];
  if (roads.length === points.length - 1 && roads.every((leg) => leg.length >= 2)) {
    return { legs: roads.map((leg) => leg.map(([lat, lon]) => [lat, lon] as LatLon)), roads: true };
  }
  const legs: LatLon[][] = [];
  for (let i = 1; i < points.length; i++) {
    legs.push([[points[i - 1].lat, points[i - 1].lon], [points[i].lat, points[i].lon]]);
  }
  return { legs, roads: false };
}

/** Planar distance, good enough to share a leg's time out over its points. */
function span(a: LatLon, b: LatLon): number {
  const dLat = b[0] - a[0];
  const dLon = (b[1] - a[1]) * Math.cos(((a[0] + b[0]) / 2) * (Math.PI / 180));
  return Math.hypot(dLat, dLon);
}

/**
 * Where the vehicle is. Progress counts legs: 0 is the depot, 1 the first stop, and in between the
 * vehicle is that far along the leg's line by distance, so it follows the road's bends.
 */
export function positionOnLegs(legs: LatLon[][], progress: number): LatLon {
  if (legs.length === 0) {
    return [0, 0];
  }
  const clamped = Math.min(Math.max(progress, 0), legs.length);
  const index = Math.min(Math.floor(clamped), legs.length - 1);
  const line = legs[index];
  const lengths = line.slice(1).map((point, i) => span(line[i], point));
  const total = lengths.reduce((sum, length) => sum + length, 0);
  let left = (clamped - index) * total;
  for (let i = 0; i < lengths.length; i++) {
    if (left <= lengths[i] || i === lengths.length - 1) {
      const t = lengths[i] === 0 ? 0 : Math.min(left / lengths[i], 1);
      return [line[i][0] + (line[i + 1][0] - line[i][0]) * t, line[i][1] + (line[i + 1][1] - line[i][1]) * t];
    }
    left -= lengths[i];
  }
  return line[line.length - 1];
}

function statusText(points: Point[], progress: number, playing: boolean): string {
  const last = points.length - 1;
  if (progress >= last - 0.02) {
    return "Rota bitti";
  }
  if (progress <= 0.02) {
    return playing ? "Depodan çıkıyor" : "Depoda";
  }
  const at = Math.round(progress);
  if (!playing && Math.abs(progress - at) < 0.05 && at > 0) {
    return `${points[at].title} durağında`;
  }
  const next = points[Math.min(Math.floor(progress) + 1, last)];
  return `${next.title} durağına gidiyor`;
}

/** A tooltip from plain text: names people typed are never read as HTML. */
function textTooltip(text: string): HTMLElement {
  const node = document.createElement("span");
  node.textContent = text;
  return node;
}

export function RouteMap({
  plan,
  warehouses = [],
  territories = [],
}: {
  plan: PlannedRoute;
  warehouses?: Warehouse[];
  territories?: Territory[];
}) {
  const points = useMemo(() => routePoints(plan), [plan]);
  const { legs, roads } = useMemo(() => legLines(plan, points), [plan, points]);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<L.Map | null>(null);
  const vehicleRef = useRef<L.CircleMarker | null>(null);
  const progressRef = useRef(0);
  const [progress, setProgress] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [seenId, setSeenId] = useState(plan.id);

  if (seenId !== plan.id) {
    setSeenId(plan.id);
    setProgress(0);
    setPlaying(false);
    progressRef.current = 0;
  } else {
    progressRef.current = progress;
  }

  useEffect(() => {
    const node = containerRef.current;
    if (!node) {
      return;
    }
    const map = L.map(node, { scrollWheelZoom: false });
    L.tileLayer(TILE_URL, {
      attribution: TILE_ATTRIBUTION,
      maxZoom: 19,
      // The console sends no Referer (nginx.conf); tile servers ask for the site's origin.
      referrerPolicy: "origin",
    }).addTo(map);
    mapRef.current = map;
    const frame = requestAnimationFrame(() => map.invalidateSize());
    return () => {
      cancelAnimationFrame(frame);
      map.remove();
      mapRef.current = null;
      vehicleRef.current = null;
    };
  }, []);

  // Background: the tenant's territories (centre and coverage radius) and warehouses.
  useEffect(() => {
    const map = mapRef.current;
    if (!map) {
      return;
    }
    const group = L.layerGroup().addTo(map);
    territories.forEach((territory) => {
      L.circle([territory.centerLatitude, territory.centerLongitude], {
        radius: territory.coverageRadiusKm * 1000,
        color: "#5b7f9e",
        weight: 1,
        fillOpacity: 0.06,
        interactive: true,
      })
        .bindTooltip(textTooltip(`${territory.territoryCode} · ${territory.name}`))
        .addTo(group);
    });
    warehouses.forEach((warehouse) => {
      L.marker([warehouse.latitude, warehouse.longitude], {
        icon: L.divIcon({ className: "route-pin", html: '<span class="warehouse">W</span>', iconSize: [22, 22], iconAnchor: [11, 11] }),
        title: `${warehouse.code} · ${warehouse.name}`,
        keyboard: false,
      }).addTo(group);
    });
    return () => {
      group.remove();
    };
  }, [territories, warehouses]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || points.length === 0) {
      return;
    }
    const group = L.layerGroup().addTo(map);
    const all = legs.flat();
    L.polyline(legs, roads
      ? { color: "#12352f", weight: 4, opacity: 0.85 }
      : { color: "#12352f", weight: 3, dashArray: "6 6" }).addTo(group);
    points.forEach((point, index) => {
      const icon = L.divIcon({
        className: "route-pin",
        html: `<span class="${index === 0 ? "depot" : "stop"}">${point.label}</span>`,
        iconSize: [28, 28],
        iconAnchor: [14, 14],
      });
      L.marker([point.lat, point.lon], { icon, title: point.title, keyboard: false }).addTo(group);
    });
    const here = positionOnLegs(legs, progressRef.current);
    vehicleRef.current = L.circleMarker(here, {
      radius: 8,
      color: "#ffffff",
      weight: 2,
      fillColor: "#c4552a",
      fillOpacity: 1,
    }).addTo(group);
    if (all.length <= 1) {
      map.setView([points[0].lat, points[0].lon], 13);
    } else {
      map.fitBounds(L.latLngBounds(all).pad(0.15));
    }
    return () => {
      group.remove();
      vehicleRef.current = null;
    };
  }, [points, legs, roads]);

  useEffect(() => {
    vehicleRef.current?.setLatLng(positionOnLegs(legs, progress));
  }, [legs, progress]);

  useEffect(() => {
    if (!playing) {
      return;
    }
    let frame = 0;
    let last = performance.now();
    const step = (now: number) => {
      const next = progressRef.current + (now - last) / LEG_MS;
      last = now;
      const end = points.length - 1;
      if (next >= end) {
        progressRef.current = end;
        setProgress(end);
        setPlaying(false);
        return;
      }
      progressRef.current = next;
      setProgress(next);
      frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [playing, points.length]);

  const finished = progress >= points.length - 1 - 0.02 && points.length > 1;

  function start() {
    if (finished) {
      progressRef.current = 0;
      setProgress(0);
    }
    setPlaying(true);
  }

  function reset() {
    progressRef.current = 0;
    setProgress(0);
    setPlaying(false);
  }

  return (
    <div className="stack">
      <div ref={containerRef} className="route-map" role="img" aria-label="Rota haritası" />
      <div className="spread">
        <span className="hint">
          {statusText(points, progress, playing)} · {roads ? "yol: OSRM" : "düz çizgi (yol bilgisi yok)"}
        </span>
        <div className="actions">
          <Button type="button" tone="quiet" onClick={reset}>Başa al</Button>
          <Button type="button" onClick={playing ? () => setPlaying(false) : start}>
            {playing ? "Duraklat" : finished ? "Yeniden oynat" : progress > 0.02 ? "Sürdür" : "Simülasyonu başlat"}
          </Button>
        </div>
      </div>
    </div>
  );
}
