import { useEffect, useMemo, useState } from 'react';
import dynamic from 'next/dynamic';
import io from 'socket.io-client';
import Head from 'next/head';

const Map = dynamic(() => import('../components/RescueMap'), { ssr: false });

export default function Dashboard() {
  const [alerts, setAlerts] = useState([]);
  const [ackedSet, setAckedSet] = useState(new Set());
  const [selected, setSelected] = useState(null);
  const [live, setLive] = useState(false);

  const serverUrl = process.env.NEXT_PUBLIC_SERVER_URL || 'http://localhost:3001';

  useEffect(() => {
    fetch(`${serverUrl}/events`)
      .then((r) => r.json())
      .then((d) => {
        setAlerts(d.sos || []);
        const confirmed = new Set((d.acks || []).map((a) => a.targetNodeId));
        setAckedSet(confirmed);
      })
      .catch(() => {});

    const socket = io(serverUrl);
    socket.on('connect', () => setLive(true));
    socket.on('disconnect', () => setLive(false));

    socket.on('sos', (ev) => {
      setAlerts((prev) => {
        const idx = prev.findIndex((a) => a.nodeId === ev.nodeId);
        if (idx >= 0) {
          const copy = [...prev];
          copy[idx] = ev;
          return copy;
        }
        return [ev, ...prev];
      });
    });

    socket.on('ack', (ack) => {
      setAckedSet((prev) => new Set([...prev, ack.targetNodeId]));
    });

    return () => socket.disconnect();
  }, [serverUrl]);

  const activeCount = useMemo(
    () => alerts.filter((a) => !ackedSet.has(a.nodeId)).length,
    [alerts, ackedSet]
  );

  const handleAccept = async (a) => {
    await fetch(`${serverUrl}/ack`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        id: crypto.randomUUID(),
        ackForSosId: a.id,
        targetNodeId: a.nodeId,
        rescuerId: 'Rescue-Command-1',
        timestamp: Date.now(),
        ttl: 7
      })
    });
    setAckedSet((prev) => new Set([...prev, a.nodeId]));
  };

  return (
    <>
      <Head>
        <title>RescueMesh Command Dashboard</title>
      </Head>

      <div className="header">
        <h1>RescueMesh</h1>
        <span className="status">
          {live ? 'Live' : 'Connecting'} | {activeCount} active alert{activeCount !== 1 ? 's' : ''}
        </span>
      </div>

      <div className="layout">
        <div className="sidebar">
          <div className="sidebar-header">Incoming SOS Alerts</div>
          {alerts.length === 0 && (
            <div style={{ padding: 24, color: '#8b949e', fontSize: 13 }}>
              No alerts yet. Waiting for mesh events...
            </div>
          )}
          {alerts.map((a) => (
            <div
              key={a.id}
              className={`alert-card ${ackedSet.has(a.nodeId) ? 'confirmed' : 'active'}`}
              onClick={() => setSelected(a)}
            >
              <div style={{ fontWeight: 600, marginBottom: 4 }}>
                {ackedSet.has(a.nodeId) ? 'Confirmed' : 'Active'} node: {(a.nodeId || '').slice(0, 8)}
              </div>
              <span className={`badge badge-${(a.sosType || '').toLowerCase()}`}>{a.sosType}</span>
              <div style={{ fontSize: 12, color: '#8b949e', marginBottom: 4 }}>
                Battery: {a.batteryLevel}% | {new Date(a.timestamp).toLocaleTimeString()}
              </div>
              <div style={{ fontSize: 12, color: '#58a6ff', fontFamily: 'monospace' }}>
                {Number(a.latitude).toFixed(5)}, {Number(a.longitude).toFixed(5)}
              </div>
              {!ackedSet.has(a.nodeId) && (
                <button
                  className="accept-btn"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleAccept(a);
                  }}
                >
                  Accept - Deploy Rescue Team
                </button>
              )}
            </div>
          ))}
        </div>

        <div className="map-container">
          <Map alerts={alerts} acked={ackedSet} selected={selected} onSelect={setSelected} />
        </div>
      </div>
    </>
  );
}
