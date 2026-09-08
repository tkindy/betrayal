(() => {
  document.addEventListener("htmx:after:swap", () => {
    const destination = document.querySelector("[data-game-url]");
    if (destination) location.assign(destination.dataset.gameUrl);
  });

  document.addEventListener("click", async (event) => {
    const button = event.target.closest("[data-copy-url]");
    if (!button) return;
    const originalLabel = button.textContent;
    try {
      const url = new URL(button.dataset.copyUrl, location.origin);
      await navigator.clipboard.writeText(url.href);
      button.textContent = "Copied!";
    } catch (_error) {
      button.textContent = "Copy failed";
    }
    setTimeout(() => {
      button.textContent = originalLabel;
    }, 1500);
  });

  const CELL_SIZE = 180;
  const viewport = document.querySelector("#board-viewport");
  if (!viewport) return;

  let view = { x: 0, y: 0, scale: 1 };
  const floorViews = {};
  let selectedFloor;
  let gesture = null;
  let initialized = false;
  let viewedPlayerId;
  let floorHoverTimer;
  let hoveredFloor;

  const svg = () => viewport.querySelector("#board");
  const world = () => viewport.querySelector("#world");
  const roomDetails = () => viewport.querySelector("#room-details");
  const playerDetails = () => viewport.querySelector("#player-details");
  let hideDetailsTimer;

  function submitHiddenForm(id, values) {
    const form = viewport.querySelector(`#${id}`);
    for (const [name, value] of Object.entries(values)) {
      form.elements.namedItem(name).value = value;
    }
    form.requestSubmit();
  }

  function applyView() {
    world()?.setAttribute(
      "transform",
      `translate(${view.x} ${view.y}) scale(${view.scale})`
    );
  }

  function floorButton(key = selectedFloor) {
    return viewport.querySelector(`[data-floor-select="${key}"]`);
  }

  function syncFloorControls() {
    const buttons = Array.from(
      viewport.querySelectorAll("[data-floor-select]")
    );
    if (!buttons.some((button) => button.dataset.floorSelect === selectedFloor)) {
      selectedFloor =
        buttons.find((button) => button.dataset.floorSelect === "ground")
          ?.dataset.floorSelect || buttons[0]?.dataset.floorSelect;
    }
    buttons.forEach((button) => {
      const active = button.dataset.floorSelect === selectedFloor;
      button.classList.toggle("active", active);
      button.setAttribute("aria-pressed", String(active));
    });
    viewport.querySelectorAll(".floor-canvas").forEach((canvas) => {
      canvas.toggleAttribute("hidden", canvas.dataset.floor !== selectedFloor);
    });
  }

  function visibleBoardArea(boardWidth, boardHeight) {
    const viewportBounds = viewport.getBoundingClientRect();
    const gap = 16;
    const width = viewport.clientWidth;
    const height = viewport.clientHeight;
    const obstacles = [
      "#floor-navigation",
      "#zoom-panel",
      "#game-sidebar",
      "#character-panel",
    ]
      .map((selector) => viewport.querySelector(selector))
      .filter((element) => element && element.getClientRects().length > 0)
      .map((element) => {
        const bounds = element.getBoundingClientRect();
        return {
          left: Math.max(0, bounds.left - viewportBounds.left - gap),
          right: Math.min(width, bounds.right - viewportBounds.left + gap),
          top: Math.max(0, bounds.top - viewportBounds.top - gap),
          bottom: Math.min(height, bounds.bottom - viewportBounds.top + gap),
        };
      });
    const xs = new Set([gap, width - gap]);
    const ys = new Set([gap, height - gap]);
    obstacles.forEach((obstacle) => {
      xs.add(obstacle.left);
      xs.add(obstacle.right);
      ys.add(obstacle.top);
      ys.add(obstacle.bottom);
    });

    let best;
    const xValues = Array.from(xs).sort((a, b) => a - b);
    const yValues = Array.from(ys).sort((a, b) => a - b);
    for (const left of xValues) {
      for (const right of xValues) {
        if (right <= left) continue;
        for (const top of yValues) {
          for (const bottom of yValues) {
            if (bottom <= top) continue;
            const intersectsControl = obstacles.some(
              (obstacle) =>
                left < obstacle.right &&
                right > obstacle.left &&
                top < obstacle.bottom &&
                bottom > obstacle.top
            );
            if (intersectsControl) continue;
            const areaWidth = right - left;
            const areaHeight = bottom - top;
            const scale = Math.min(
              1,
              areaWidth / boardWidth,
              areaHeight / boardHeight
            );
            const area = areaWidth * areaHeight;
            if (
              !best ||
              scale > best.scale ||
              (scale === best.scale && area > best.area)
            ) {
              best = {
                left,
                top,
                width: areaWidth,
                height: areaHeight,
                scale,
                area,
              };
            }
          }
        }
      }
    }
    return (
      best || {
        left: gap,
        top: gap,
        width: Math.max(1, width - gap * 2),
        height: Math.max(1, height - gap * 2),
      }
    );
  }

  function fitBoard() {
    const state = floorButton();
    if (!state) return;
    const widthInCells =
      Number(state.dataset.maxX) - Number(state.dataset.minX) + 1;
    const heightInCells =
      Number(state.dataset.maxY) - Number(state.dataset.minY) + 1;
    const boardWidth = widthInCells * CELL_SIZE;
    const boardHeight = heightInCells * CELL_SIZE;
    const available = visibleBoardArea(boardWidth, boardHeight);
    view.scale = Math.min(
      1,
      available.width / boardWidth,
      available.height / boardHeight
    );
    view.x =
      available.left +
      (available.width - boardWidth * view.scale) / 2 -
      Number(state.dataset.minX) * CELL_SIZE * view.scale;
    view.y =
      available.top +
      (available.height - boardHeight * view.scale) / 2 -
      Number(state.dataset.minY) * CELL_SIZE * view.scale;
    initialized = true;
    applyView();
  }

  function selectFloor(key, fit = false) {
    if (!key || key === selectedFloor) return;
    if (selectedFloor) floorViews[selectedFloor] = { ...view };
    selectedFloor = key;
    syncFloorControls();
    if (floorViews[key] && !fit) {
      view = { ...floorViews[key] };
      applyView();
    } else {
      fitBoard();
    }
    if (gesture?.type === "piece") gesture.changedFloor = true;
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
    const roomActions = roomDetails()?.querySelector(".room-actions-menu");
    if (roomActions) roomActions.open = false;
  }

  function scheduleHideBoardDetails() {
    clearTimeout(hideDetailsTimer);
    hideDetailsTimer = setTimeout(hideBoardDetails, 150);
  }

  function positionBoardDetails(details, target) {
    const viewportBox = viewport.getBoundingClientRect();
    const targetBox = target.getBoundingClientRect();
    const gap = 16;
    const right = targetBox.right - viewportBox.left + gap;
    const left =
      targetBox.left - viewportBox.left - details.offsetWidth - gap;
    const desiredX =
      right + details.offsetWidth <= viewport.clientWidth - gap ? right : left;
    const desiredY = targetBox.top - viewportBox.top;
    details.style.left = `${Math.max(gap, desiredX)}px`;
    details.style.top = `${Math.max(
      gap,
      Math.min(desiredY, viewport.clientHeight - details.offsetHeight - gap)
    )}px`;
  }

  function showRoomDetails(room) {
    clearTimeout(hideDetailsTimer);
    const details = roomDetails();
    if (!details) return;
    const roomActions = details.querySelector(".room-actions-menu");
    const actionsAvailable = room.matches(".room-cell");
    const detailsKey = actionsAvailable ? `board-${room.dataset.id}` : "room-stack";
    if (details.dataset.roomKey !== detailsKey && roomActions) {
      roomActions.open = false;
    }
    details.dataset.roomKey = detailsKey;
    if (roomActions) roomActions.hidden = !actionsAvailable;
    const playerCard = playerDetails();
    if (playerCard) playerCard.hidden = true;
    details.querySelector(".room-details-name").textContent =
      room.dataset.roomName;
    details.querySelector(".room-details-description").textContent =
      room.dataset.description || "No additional room rules.";
    if (actionsAvailable) {
      details.querySelectorAll(".room-details-id").forEach((input) => {
        input.value = room.dataset.id;
      });
    }
    details.hidden = false;
    details.setAttribute("aria-hidden", "false");
    positionBoardDetails(details, room);
  }

  function showPlayerDetails(player) {
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
    positionBoardDetails(details, player);
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

    const room = event.target.closest(
      ".room-cell, .room-picker-preview.flipped"
    );
    if (room) {
      showRoomDetails(room);
      return;
    }

    const player = event.target.closest(".token.player");
    if (player) {
      showPlayerDetails(player);
      return;
    }

    scheduleHideBoardDetails();
  }

  function beginGesture(event) {
    if (event.button !== 0) return;
    if (
      event.target.closest(
        "#game-ui, #floor-navigation, #room-details, .open-spot"
      )
    )
      return;
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
        point,
        changedFloor: false,
      };
      piece.classList.add("dragging");
      companion?.classList.add("dragging");
      const preview = piece.cloneNode(true);
      preview.removeAttribute("transform");
      preview.removeAttribute("id");
      preview.classList.add("drag-preview");
      viewport.querySelector("#drag-layer").append(preview);
      gesture.preview = preview;
      updateDragPreview(event);
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

  function updateDragPreview(event) {
    if (!gesture?.preview) return;
    const bounds = viewport.getBoundingClientRect();
    const room = gesture.element.dataset.kind === "room";
    const scale = room ? 0.45 : 1.25;
    const offset = room ? -CELL_SIZE / 2 : 0;
    gesture.preview.setAttribute(
      "transform",
      `translate(${event.clientX - bounds.left} ${
        event.clientY - bounds.top
      }) scale(${scale}) translate(${offset} ${offset})`
    );
  }

  function updateFloorHover(event) {
    if (gesture?.type !== "piece") return;
    const button = document
      .elementFromPoint(event.clientX, event.clientY)
      ?.closest("[data-floor-select]");
    const key = button?.dataset.floorSelect;
    if (!key || key === selectedFloor) {
      clearTimeout(floorHoverTimer);
      hoveredFloor = null;
      return;
    }
    if (key === hoveredFloor) return;
    clearTimeout(floorHoverTimer);
    hoveredFloor = key;
    floorHoverTimer = setTimeout(() => {
      selectFloor(key, true);
      hoveredFloor = null;
    }, 700);
  }

  function updateGesture(event) {
    if (!gesture || gesture.pointerId !== event.pointerId) return;
    if (gesture.type === "pan") {
      view.x = gesture.origin.x + event.clientX - gesture.point.x;
      view.y = gesture.origin.y + event.clientY - gesture.point.y;
      applyView();
      return;
    }
    updateDragPreview(event);
    updateFloorHover(event);
  }

  function finishGesture(event) {
    if (!gesture || gesture.pointerId !== event.pointerId) return;
    const completed = gesture;
    gesture = null;
    clearTimeout(floorHoverTimer);
    hoveredFloor = null;
    svg().classList.remove("panning");
    if (completed.type !== "piece") return;

    completed.element.classList.remove("dragging");
    completed.companion?.classList.remove("dragging");
    completed.preview?.remove();
    const dropTarget = document.elementFromPoint(event.clientX, event.clientY);
    if (dropTarget?.closest("#floor-navigation, #game-ui")) return;
    const point = clientToWorld(event.clientX, event.clientY);
    let gridX;
    let gridY;
    if (completed.element.dataset.kind === "room") {
      if (completed.changedFloor) {
        gridX = Math.floor(point.x / CELL_SIZE);
        gridY = Math.floor(point.y / CELL_SIZE);
      } else {
        const x = completed.origin.x + point.x - completed.point.x;
        const y = completed.origin.y + point.y - completed.point.y;
        gridX = Math.round(x / CELL_SIZE);
        gridY = Math.round(y / CELL_SIZE);
      }
    } else {
      gridX = Math.floor(point.x / CELL_SIZE);
      gridY = Math.floor(point.y / CELL_SIZE);
    }

    const { kind, id } = completed.element.dataset;
    submitHiddenForm("move-command", {
      kind,
      id,
      "grid-x": String(gridX),
      "grid-y": String(gridY),
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

  function placeRoom(event) {
    const spot = event.target.closest(".open-spot");
    if (!spot) return;
    submitHiddenForm("place-room-command", {
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

  function changeFloor(event) {
    const button = event.target.closest("[data-floor-select]");
    if (!button) return;
    selectFloor(button.dataset.floorSelect);
  }

  viewport.addEventListener("pointerdown", beginGesture);
  viewport.addEventListener("pointermove", (event) => {
    updateGesture(event);
    updateBoardDetails(event);
  });
  viewport.addEventListener("pointerleave", hideBoardDetails);
  viewport.addEventListener("pointerup", finishGesture);
  viewport.addEventListener("pointercancel", finishGesture);
  viewport.addEventListener("toggle", enforceSingleOpenCard, true);
  viewport.addEventListener("change", selectViewedPlayer);
  viewport.addEventListener("click", closeInventoryCard);
  viewport.addEventListener("click", changeBoardView);
  viewport.addEventListener("click", changeFloor);
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
  document.addEventListener("htmx:after:swap", () => {
    syncFloorControls();
    applyView();
    syncCharacterPanel();
  });
  syncCharacterPanel();
  syncFloorControls();
  fitBoard();
})();
