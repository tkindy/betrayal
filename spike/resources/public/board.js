(() => {
  const CELL_SIZE = 180;
  const viewport = document.querySelector("#board-viewport");
  if (!viewport) return;

  let view = { x: 0, y: 0, scale: 1 };
  let gesture = null;
  let initialized = false;
  let socket;
  let reconnectTimer;
  let viewedPlayerId;
  const commandQueue = [];

  const svg = () => viewport.querySelector("#board");
  const world = () => viewport.querySelector("#world");
  const roomDetails = () => viewport.querySelector("#room-details");

  function connect() {
    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    const socketUrl = new URL(
      `${protocol}//${location.host}/games/${viewport.dataset.gameId}/socket`
    );
    if (viewport.dataset.debugPlayerId) {
      socketUrl.searchParams.set("player-id", viewport.dataset.debugPlayerId);
    }
    socket = new WebSocket(socketUrl);
    socket.addEventListener("open", () => {
      clearTimeout(reconnectTimer);
      while (commandQueue.length) {
        socket.send(JSON.stringify(commandQueue.shift()));
      }
    });
    socket.addEventListener("message", (event) => {
      swapGameFragments(event.data);
    });
    socket.addEventListener("close", () => {
      reconnectTimer = setTimeout(connect, 1000);
    });
  }

  function sendCommand(command) {
    if (socket?.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify(command));
    } else {
      commandQueue.push(command);
    }
  }

  function applyView() {
    world()?.setAttribute(
      "transform",
      `translate(${view.x} ${view.y}) scale(${view.scale})`
    );
  }

  function fitBoard() {
    const state = viewport.querySelector("#board-state");
    const widthInCells =
      Number(state.dataset.maxX) - Number(state.dataset.minX) + 1;
    const heightInCells =
      Number(state.dataset.maxY) - Number(state.dataset.minY) + 1;
    const padding = 80;
    const availableWidth = viewport.clientWidth - padding * 2;
    const availableHeight = viewport.clientHeight - padding * 2;
    view.scale = Math.min(
      1,
      availableWidth / (widthInCells * CELL_SIZE),
      availableHeight / (heightInCells * CELL_SIZE)
    );
    view.x =
      (viewport.clientWidth - widthInCells * CELL_SIZE * view.scale) / 2 -
      Number(state.dataset.minX) * CELL_SIZE * view.scale;
    view.y =
      (viewport.clientHeight - heightInCells * CELL_SIZE * view.scale) / 2 -
      Number(state.dataset.minY) * CELL_SIZE * view.scale;
    initialized = true;
    applyView();
  }

  function clientToWorld(clientX, clientY) {
    const point = new DOMPoint(clientX, clientY);
    return point.matrixTransform(world().getScreenCTM().inverse());
  }

  function parseTranslate(element) {
    const transform = element.getAttribute("transform") || "";
    const match = transform.match(
      /translate\(\s*([-.\d]+)[ ,]\s*([-.\d]+)\s*\)/
    );
    return match
      ? { x: Number(match[1]), y: Number(match[2]) }
      : { x: 0, y: 0 };
  }

  function hideRoomDetails() {
    const details = roomDetails();
    if (!details) return;
    details.hidden = true;
    details.setAttribute("aria-hidden", "true");
  }

  function updateRoomDetails(event) {
    if (gesture) {
      hideRoomDetails();
      return;
    }

    const room = event.target.closest(".room-cell");
    const details = roomDetails();
    if (!room || !details) {
      hideRoomDetails();
      return;
    }

    details.querySelector(".room-details-name").textContent =
      room.dataset.roomName;
    details.querySelector(".room-details-description").textContent =
      room.dataset.description || "No additional room rules.";
    details.hidden = false;
    details.setAttribute("aria-hidden", "false");

    const viewportBox = viewport.getBoundingClientRect();
    const gap = 16;
    const desiredX = event.clientX - viewportBox.left + gap;
    const desiredY = event.clientY - viewportBox.top + gap;
    details.style.left = `${Math.max(
      gap,
      Math.min(desiredX, viewport.clientWidth - details.offsetWidth - gap)
    )}px`;
    details.style.top = `${Math.max(
      gap,
      Math.min(desiredY, viewport.clientHeight - details.offsetHeight - gap)
    )}px`;
  }

  function beginGesture(event) {
    if (event.button !== 0) return;
    if (event.target.closest("#game-ui, .open-spot")) return;
    hideRoomDetails();
    const piece = event.target.closest(".draggable");
    if (piece) {
      const point = clientToWorld(event.clientX, event.clientY);
      const companion =
        piece.dataset.kind === "room"
          ? viewport.querySelector(
              `.agents[data-grid-x="${piece.dataset.gridX}"][data-grid-y="${piece.dataset.gridY}"]`
            )
          : null;
      gesture = {
        type: "piece",
        pointerId: event.pointerId,
        element: piece,
        origin: parseTranslate(piece),
        companion,
        companionOrigin: companion ? parseTranslate(companion) : null,
        point,
      };
      piece.classList.add("dragging");
      companion?.classList.add("dragging");
    } else {
      gesture = {
        type: "pan",
        pointerId: event.pointerId,
        point: { x: event.clientX, y: event.clientY },
        origin: { x: view.x, y: view.y },
      };
      svg().classList.add("panning");
    }
    svg().setPointerCapture(event.pointerId);
  }

  function updateGesture(event) {
    if (!gesture || gesture.pointerId !== event.pointerId) return;
    if (gesture.type === "pan") {
      view.x = gesture.origin.x + event.clientX - gesture.point.x;
      view.y = gesture.origin.y + event.clientY - gesture.point.y;
      applyView();
      return;
    }
    const point = clientToWorld(event.clientX, event.clientY);
    const x = gesture.origin.x + point.x - gesture.point.x;
    const y = gesture.origin.y + point.y - gesture.point.y;
    gesture.element.setAttribute("transform", `translate(${x} ${y})`);
    if (gesture.companion) {
      const companionX =
        gesture.companionOrigin.x + point.x - gesture.point.x;
      const companionY =
        gesture.companionOrigin.y + point.y - gesture.point.y;
      gesture.companion.setAttribute(
        "transform",
        `translate(${companionX} ${companionY})`
      );
    }
  }

  function finishGesture(event) {
    if (!gesture || gesture.pointerId !== event.pointerId) return;
    const completed = gesture;
    gesture = null;
    svg().classList.remove("panning");
    if (completed.type !== "piece") return;

    completed.element.classList.remove("dragging");
    completed.companion?.classList.remove("dragging");
    const point = clientToWorld(event.clientX, event.clientY);
    let gridX;
    let gridY;
    if (completed.element.dataset.kind === "room") {
      const x = completed.origin.x + point.x - completed.point.x;
      const y = completed.origin.y + point.y - completed.point.y;
      gridX = Math.round(x / CELL_SIZE);
      gridY = Math.round(y / CELL_SIZE);
    } else {
      gridX = Math.floor(point.x / CELL_SIZE);
      gridY = Math.floor(point.y / CELL_SIZE);
    }

    const { kind, id } = completed.element.dataset;
    sendCommand({
      command: "move",
      kind,
      id,
      "grid-x": String(gridX),
      "grid-y": String(gridY),
    });
  }

  function swapGameFragments(html) {
    const document = new DOMParser().parseFromString(html, "text/html");
    const payload = document.querySelector("#game-fragments");
    if (!payload) return;

    const fragments = Array.from(payload.children).flatMap((element) =>
      element.id === "ui-updates" ? Array.from(element.children) : [element]
    );
    let boardChanged = false;
    for (const fragment of fragments) {
      if (!fragment.id) continue;
      const current = viewport.querySelector(`#${CSS.escape(fragment.id)}`);
      if (!current) continue;
      const openDetails = new Set(
        Array.from(current.querySelectorAll("details[open][id]"), (details) =>
          details.id
        )
      );
      current.replaceWith(fragment);
      for (const detailsId of openDetails) {
        viewport.querySelector(`#${CSS.escape(detailsId)}`)?.setAttribute(
          "open",
          ""
        );
      }
      boardChanged ||= fragment.id === "board-state";
    }
    if (boardChanged) applyView();
    syncCharacterPanel();
    viewport
      .querySelectorAll("form.game-action button:disabled")
      .forEach((button) => {
        button.disabled = false;
      });
  }

  function syncCharacterPanel() {
    const select = viewport.querySelector("#player-select");
    if (!select) return;
    if (
      !viewedPlayerId ||
      !Array.from(select.options).some(
        (option) => option.value === viewedPlayerId
      )
    ) {
      viewedPlayerId = select.value;
    }
    select.value = viewedPlayerId;
    viewport
      .querySelectorAll("[data-viewed-player-id]")
      .forEach((content) => {
        content.hidden = content.dataset.viewedPlayerId !== viewedPlayerId;
      });
  }

  function selectViewedPlayer(event) {
    if (!event.target.matches("#player-select")) return;
    viewedPlayerId = event.target.value;
    syncCharacterPanel();
  }

  function submitGameAction(event) {
    const form = event.target.closest("form.game-action");
    if (!form) return;
    event.preventDefault();
    const button = event.submitter;
    if (button) button.disabled = true;
    sendCommand({
      command: "action",
      action: form.dataset.action,
      ...Object.fromEntries(new FormData(form)),
    });
  }

  function placeRoom(event) {
    const spot = event.target.closest(".open-spot");
    if (!spot) return;
    sendCommand({
      command: "action",
      action: "place-room",
      "grid-x": spot.dataset.gridX,
      "grid-y": spot.dataset.gridY,
    });
  }

  viewport.addEventListener("pointerdown", beginGesture);
  viewport.addEventListener("pointermove", (event) => {
    updateGesture(event);
    updateRoomDetails(event);
  });
  viewport.addEventListener("pointerleave", hideRoomDetails);
  viewport.addEventListener("pointerup", finishGesture);
  viewport.addEventListener("pointercancel", finishGesture);
  viewport.addEventListener("submit", submitGameAction);
  viewport.addEventListener("change", selectViewedPlayer);
  viewport.addEventListener("click", placeRoom);
  viewport.addEventListener(
    "wheel",
    (event) => {
      if (event.target.closest("#game-ui")) return;
      event.preventDefault();
      const before = clientToWorld(event.clientX, event.clientY);
      const factor = Math.exp(-event.deltaY * 0.001);
      view.scale = Math.min(2.5, Math.max(0.2, view.scale * factor));
      applyView();
      const after = clientToWorld(event.clientX, event.clientY);
      view.x += (after.x - before.x) * view.scale;
      view.y += (after.y - before.y) * view.scale;
      applyView();
    },
    { passive: false }
  );

  window.addEventListener("resize", () => {
    if (!initialized) fitBoard();
  });
  syncCharacterPanel();
  fitBoard();
  connect();
})();
