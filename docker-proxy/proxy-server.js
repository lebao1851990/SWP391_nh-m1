const http = require('node:http');

const PORT = process.env.PORT || 2375;
const DOCKER_SOCKET = process.env.DOCKER_SOCKET || '/var/run/docker.sock';
const ALLOWED_IMAGE = 'eclipse-temurin:21-jdk-alpine';
const MAX_MEMORY_BYTES = 512 * 1024 * 1024;
const MAX_NANO_CPUS = 500_000_000;
const MAX_PIDS = 50;

// Set of container IDs created and authorized by this security proxy
const trackedSandboxContainers = new Set();

function isTrackedContainer(id) {
  if (!id) return false;
  for (const tracked of trackedSandboxContainers) {
    if (tracked === id || tracked.startsWith(id) || id.startsWith(tracked)) {
      return true;
    }
  }
  return false;
}

function removeTrackedContainer(id) {
  if (!id) return;
  for (const tracked of trackedSandboxContainers) {
    if (tracked === id || tracked.startsWith(id) || id.startsWith(tracked)) {
      trackedSandboxContainers.delete(tracked);
    }
  }
}

function forwardToDocker(clientReq, clientRes, pathOverride = null, bodyOverride = null, onResponse = null) {
  const options = {
    socketPath: DOCKER_SOCKET,
    path: pathOverride || clientReq.url,
    method: clientReq.method,
    headers: { ...clientReq.headers },
  };
  delete options.headers.host;

  if (bodyOverride) {
    options.headers['content-length'] = Buffer.byteLength(bodyOverride);
  }

  const dockerReq = http.request(options, (dockerRes) => {
    if (onResponse) {
      const chunks = [];
      dockerRes.on('data', (c) => chunks.push(c));
      dockerRes.on('end', () => {
        const fullBuffer = Buffer.concat(chunks);
        onResponse(dockerRes.statusCode, dockerRes.headers, fullBuffer);
        clientRes.writeHead(dockerRes.statusCode, dockerRes.headers);
        clientRes.end(fullBuffer);
      });
    } else {
      clientRes.writeHead(dockerRes.statusCode, dockerRes.headers);
      dockerRes.pipe(clientRes);
    }
  });

  dockerReq.on('error', (err) => {
    console.error('[DockerProxy] Socket error:', err.message);
    if (!clientRes.headersSent) {
      clientRes.writeHead(502, { 'Content-Type': 'application/json' });
      clientRes.end(JSON.stringify({ error: 'Docker daemon proxy communication error: ' + err.message }));
    }
  });

  if (bodyOverride) {
    dockerReq.write(bodyOverride);
    dockerReq.end();
  } else {
    clientReq.pipe(dockerReq);
  }
}

const server = http.createServer((req, res) => {
  const url = req.url;
  const method = req.method.toUpperCase();
  const [pathname] = url.split('?');

  // 1. Healthcheck / Ping
  if (pathname === '/_ping') {
    return forwardToDocker(req, res);
  }

  // 2. Info / Version
  if (/^\/v[0-9.]+\/(version|info)$/.test(pathname) && method === 'GET') {
    return forwardToDocker(req, res);
  }

  // 3. Container Creation: POST /v1.*/containers/create
  if (/^\/v[0-9.]+\/containers\/create/.test(pathname) && method === 'POST') {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => {
      const rawBody = Buffer.concat(chunks).toString('utf-8');
      let payload;
      try {
        payload = JSON.parse(rawBody);
      } catch (e) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ error: 'Malformed JSON payload' }));
      }

      // Assert Image
      if (payload.Image !== ALLOWED_IMAGE) {
        console.warn(`[DockerProxy] Rejected container creation: unauthorized image '${payload.Image}'`);
        res.writeHead(403, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ error: `Forbidden: Only '${ALLOWED_IMAGE}' image is permitted by Sandbox Security Firewall.` }));
      }

      const hostConfig = payload.HostConfig || {};

      // Assert Privileged
      if (hostConfig.Privileged === true) {
        console.warn('[DockerProxy] Rejected container creation: privileged mode requested');
        res.writeHead(403, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ error: 'Forbidden: Privileged containers are strictly prohibited by Sandbox Security Firewall.' }));
      }

      // Assert Binds / Volumes
      if (hostConfig.Binds && Array.isArray(hostConfig.Binds) && hostConfig.Binds.length > 0) {
        console.warn('[DockerProxy] Rejected container creation: host volume binds requested', hostConfig.Binds);
        res.writeHead(403, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ error: 'Forbidden: Host filesystem bind mounts are prohibited by Sandbox Security Firewall.' }));
      }

      // Assert Network Isolation
      const netDisabled = payload.NetworkDisabled === true || hostConfig.NetworkMode === 'none';
      if (!netDisabled) {
        console.warn('[DockerProxy] Rejected container creation: network is not disabled');
        res.writeHead(403, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ error: 'Forbidden: Network isolation is mandatory (NetworkMode must be none).' }));
      }

      // Assert ReadonlyRootfs
      if (hostConfig.ReadonlyRootfs !== true) {
        console.warn('[DockerProxy] Rejected container creation: ReadonlyRootfs is false');
        res.writeHead(403, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ error: 'Forbidden: Read-only root filesystem is mandatory.' }));
      }

      // Assert Capabilities
      if (!hostConfig.CapDrop || !hostConfig.CapDrop.includes('ALL')) {
        console.warn('[DockerProxy] Rejected container creation: CapDrop ALL missing');
        res.writeHead(403, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ error: 'Forbidden: CapDrop ALL is mandatory.' }));
      }

      // Assert Resource Limits
      if (hostConfig.Memory && hostConfig.Memory > MAX_MEMORY_BYTES) {
        res.writeHead(403, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ error: `Forbidden: Memory limit exceeds ${MAX_MEMORY_BYTES} bytes.` }));
      }
      if (hostConfig.NanoCPUs && hostConfig.NanoCPUs > MAX_NANO_CPUS) {
        res.writeHead(403, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ error: `Forbidden: CPU quota exceeds ${MAX_NANO_CPUS} nanoCPUs.` }));
      }
      if (hostConfig.PidsLimit && hostConfig.PidsLimit > MAX_PIDS) {
        res.writeHead(403, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ error: `Forbidden: PidsLimit exceeds ${MAX_PIDS}.` }));
      }

      // Assert Runtime User
      if (payload.User !== '65534:65534') {
        console.warn('[DockerProxy] Rejected container creation: User is not nobody:nobody');
        res.writeHead(403, { 'Content-Type': 'application/json' });
        return res.end(JSON.stringify({ error: 'Forbidden: Container must run as non-root user 65534:65534.' }));
      }

      // Forward validated create request and track container ID
      forwardToDocker(req, res, url, rawBody, (statusCode, headers, respBuffer) => {
        if (statusCode === 201) {
          try {
            const respObj = JSON.parse(respBuffer.toString('utf-8'));
            if (respObj.Id) {
              trackedSandboxContainers.add(respObj.Id);
              console.log(`[DockerProxy] Authorized and registered sandbox container: ${respObj.Id.substring(0, 12)}`);
            }
          } catch (e) {
            console.error('[DockerProxy] Failed to parse create response:', e.message);
          }
        }
      });
    });
    return;
  }

  // 4. Scoped Container Operations on Tracked Containers ONLY:
  // e.g. /v1.*/containers/:id/(start|wait|logs|stop) or DELETE /v1.*/containers/:id
  const containerMatch = pathname.match(/^\/v[0-9.]+\/containers\/([a-zA-Z0-9_-]+)(?:\/(start|wait|logs|stop))?$/);
  if (containerMatch) {
    const targetId = containerMatch[1];
    const subAction = containerMatch[2]; // 'start', 'wait', 'logs', 'stop', or undefined

    // Verify ownership
    if (!isTrackedContainer(targetId)) {
      console.warn(`[DockerProxy] Blocked unauthorized operation on non-sandbox container: ${targetId} (${method} ${url})`);
      res.writeHead(403, { 'Content-Type': 'application/json' });
      return res.end(JSON.stringify({
        error: `Forbidden: Container '${targetId}' is not owned or tracked by Sandbox Security Broker. Access to host containers is prohibited.`
      }));
    }

    if (method === 'DELETE') {
      return forwardToDocker(req, res, null, null, (statusCode) => {
        if (statusCode === 200 || statusCode === 204) {
          removeTrackedContainer(targetId);
          console.log(`[DockerProxy] Deregistered removed sandbox container: ${targetId.substring(0, 12)}`);
        }
      });
    }

    if (method === 'POST' && (subAction === 'start' || subAction === 'wait' || subAction === 'stop')) {
      return forwardToDocker(req, res);
    }

    if (method === 'GET' && subAction === 'logs') {
      return forwardToDocker(req, res);
    }
  }

  // 5. Everything else is STRICTLY FORBIDDEN
  console.warn(`[DockerProxy] Blocked prohibited Docker API request: ${method} ${url}`);
  res.writeHead(403, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({
    error: `Forbidden: Docker API endpoint '${method} ${url}' is blocked by Sandbox Security Firewall.`
  }));
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`[DockerProxy] Hardened Authorizing Docker Socket Security Proxy listening on 0.0.0.0:${PORT}`);
});
