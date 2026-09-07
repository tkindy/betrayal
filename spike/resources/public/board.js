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
  const playerDetails = () => viewport.querySelector("#player-details");
  let hideDetailsTimer;

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

  function zoomAt(clientX, clientY, factor) {
    const before = clientToWorld(clientX, clientY);
    view.scale = Math.min(2.5, Math.max(0.2, view.scale * factor));
    applyView();
    const after = clientToWorld(clientX, clientY);
    view.x += (after.x - before.x) * view.scale;
    view.y += (after.y - before.y) * view.scale;
    applyView();
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

  function hideBoardDetails() {
    clearTimeout(hideDetailsTimer);
    for (const details of [roomDetails(), playerDetails()]) {
      if (!details) continue;
      details.hidden = true;
      details.setAttribute("aria-hidden", "true");
    }
  }

  function scheduleHideBoardDetails() {
    clearTimeout(hideDetailsTimer);
    hideDetailsTimer = setTimeout(hideBoardDetails, 150);
  }

  function positionBoardDetails(details, event) {
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

  function showRoomDetails(room, event) {
    clearTimeout(hideDetailsTimer);
    const details = roomDetails();
    if (!details) return;
    const playerCard = playerDetails();
    if (playerCard) playerCard.hidden = true;
    details.querySelector(".room-details-name").textContent =
      room.dataset.roomName;
    details.querySelector(".room-details-description").textContent =
      room.dataset.description || "No additional room rules.";
    details.querySelectorAll(".room-details-id").forEach((input) => {
      input.value = room.dataset.id;
    });
    details.hidden = false;
    details.setAttribute("aria-hidden", "false");
    positionBoardDetails(details, event);
  }

  function showPlayerDetails(player, event) {
    clearTimeout(hideDetailsTimer);
    const details = playerDetails();
    if (!details) return;
    const roomCard = roomDetails();
    if (roomCard) roomCard.hidden = true;
    details.querySelector(".player-details-name").textContent =
      player.dataset.playerName;
    details.querySelector(".player-details-character").textContent =
      player.dataset.characterName;
    for (const trait of ["speed", "might", "sanity", "knowledge"]) {
      details.querySelector(`.player-details-${trait}`).textContent =
        player.dataset[trait];
    }
    details.hidden = false;
    details.setAttribute("aria-hidden", "false");
    positionBoardDetails(details, event);
  }

  function updateBoardDetails(event) {
    if (gesture) {
      hideBoardDetails();
      return;
    }

    if (event.target.closest("#room-details")) {
      clearTimeout(hideDetailsTimer);
      return;
    }

    const room = event.target.closest(".room-cell");
    if (room) {
      showRoomDetails(room, event);
      return;
    }

    const player = event.target.closest(".token.player");
    if (player) {
      showPlayerDetails(player, event);
      return;
    }

    scheduleHideBoardDetails();
  }

  function beginGesture(event) {
    if (event.button !== 0) return;
    if (event.target.closest("#game-ui, #room-details, .open-spot")) return;
    hideBoardDetails();
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

  function closeInventoryCards(except) {
    viewport.querySelectorAll("details.inventory-card[open]").forEach((card) => {
      if (card !== except) card.open = false;
    });
  }

  function enforceSingleOpenCard(event) {
    const card = event.target;
    if (!card.matches("details.inventory-card") || !card.open) return;
    closeInventoryCards(card);
  }

  function closeInventoryCard(event) {
    const button = event.target.closest(".inventory-card-close");
    if (!button) return;
    event.preventDefault();
    button.closest("details.inventory-card").open = false;
  }

  function selectViewedPlayer(event) {
    if (!event.target.matches("#player-select")) return;
    closeInventoryCards();
    viewedPlayerId = event.target.value;
    syncCharacterPanel();
  }

  function submitGameAction(event) {
    const form = event.target.closest("form.game-action");
    if (!form) return;
    event.preventDefault();
    if (form.dataset.confirm && !window.confirm(form.dataset.confirm)) return;
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

  function changeBoardView(event) {
    const button = event.target.closest("[data-board-view]");
    if (!button) return;
    if (button.dataset.boardView === "fit") {
      fitBoard();
      return;
    }
    const bounds = viewport.getBoundingClientRect();
    zoomAt(
      bounds.left + bounds.width / 2,
      bounds.top + bounds.height / 2,
      button.dataset.boardView === "zoom-in" ? 1.25 : 0.8
    );
  }

  viewport.addEventListener("pointerdown", beginGesture);
  viewport.addEventListener("pointermove", (event) => {
    updateGesture(event);
    updateBoardDetails(event);
  });
  viewport.addEventListener("pointerleave", hideBoardDetails);
  viewport.addEventListener("pointerup", finishGesture);
  viewport.addEventListener("pointercancel", finishGesture);
  viewport.addEventListener("submit", submitGameAction);
  viewport.addEventListener("toggle", enforceSingleOpenCard, true);
  viewport.addEventListener("change", selectViewedPlayer);
  viewport.addEventListener("click", closeInventoryCard);
  viewport.addEventListener("click", changeBoardView);
  viewport.addEventListener("click", placeRoom);
  viewport.addEventListener(
    "wheel",
    (event) => {
      if (event.target.closest("#game-ui")) return;
      event.preventDefault();
      const factor = Math.exp(-event.deltaY * 0.001);
      zoomAt(event.clientX, event.clientY, factor);
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
