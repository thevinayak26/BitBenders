import { useEffect } from 'react';
import { CircleMarker, MapContainer, Popup, TileLayer, useMap } from 'react-leaflet';

function FlyTo({ selected }) {
  const map = useMap();
  useEffect(() => {
    if (selected && Number.isFinite(selected.latitude) && Number.isFinite(selected.longitude)) {
      map.flyTo([selected.latitude, selected.longitude], 15);
    }
  }, [selected, map]);
  return null;
}

export default function RescueMap({ alerts, acked, selected, onSelect }) {
  return (
    <MapContainer center={[28.6139, 77.209]} zoom={12} style={{ height: '100%', width: '100%' }}>
      <TileLayer attribution="OpenStreetMap" url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
      <FlyTo selected={selected} />
      {alerts.map((a) => {
        if (!Number.isFinite(a.latitude) || !Number.isFinite(a.longitude)) return null;
        const confirmed = acked.has(a.nodeId);
        const color = confirmed ? '#3fb950' : '#f85149';

        return (
          <CircleMarker
            key={a.id}
            center={[a.latitude, a.longitude]}
            radius={selected?.id === a.id ? 18 : 12}
            pathOptions={{ color, fillColor: color, fillOpacity: confirmed ? 0.4 : 0.8, weight: 2 }}
            eventHandlers={{ click: () => onSelect(a) }}
          >
            <Popup>
              <div style={{ fontFamily: 'monospace', fontSize: 13 }}>
                <strong>Node:</strong> {a.nodeId}
                <br />
                <strong>Type:</strong> {a.sosType}
                <br />
                <strong>Battery:</strong> {a.batteryLevel}%
                <br />
                <strong>Hops used:</strong> {7 - (a.ttl ?? 7)}
                <br />
                <strong>Time:</strong> {new Date(a.timestamp).toLocaleTimeString()}
                <br />
                <strong>Status:</strong> {confirmed ? 'Rescue dispatched' : 'Active SOS'}
              </div>
            </Popup>
          </CircleMarker>
        );
      })}
    </MapContainer>
  );
}
