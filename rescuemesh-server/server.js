const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');

const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: '*', methods: ['GET', 'POST'] } });

app.use(cors());
app.use(express.json());

const sosEvents = [];
const ackEvents = [];
const activeAlerts = new Map();

app.post('/sos', (req, res) => {
  const event = { ...req.body, receivedAt: Date.now() };
  sosEvents.push(event);
  if (event.nodeId) {
    activeAlerts.set(event.nodeId, event);
  }
  console.log(`[SOS] Node ${event.nodeId} @ ${event.latitude},${event.longitude} | ${event.sosType}`);
  io.emit('sos', event);
  res.sendStatus(200);
});

app.post('/ack', (req, res) => {
  const ack = { ...req.body, issuedAt: Date.now() };
  ackEvents.push(ack);
  if (ack.targetNodeId) {
    activeAlerts.delete(ack.targetNodeId);
  }
  console.log(`[ACK] ${ack.rescuerId} accepted SOS from ${ack.targetNodeId}`);
  io.emit('ack', ack);
  io.to('gateways').emit('relay_ack', ack);
  res.sendStatus(200);
});

app.get('/events', (req, res) => {
  res.json({
    sos: sosEvents,
    acks: ackEvents,
    active: Array.from(activeAlerts.values())
  });
});

app.get('/', (req, res) => {
  res.json({ status: 'RescueMesh server running' });
});

io.on('connection', (socket) => {
  console.log(`[WS] Connected: ${socket.id}`);
  socket.on('register_gateway', (data) => {
    socket.join('gateways');
    console.log(`[WS] Gateway registered: ${data?.nodeId || 'unknown'}`);
  });
});

const PORT = process.env.PORT || 3001;
server.listen(PORT, () => {
  console.log(`RescueMesh server listening on port ${PORT}`);
});
