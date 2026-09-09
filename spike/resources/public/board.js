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
  const GRID_SIZE = 32;
  const BOARD_DETAILS_DELAY = 150;
  const FLOOR_DRAWER_EDGE_ZONE = 64;
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
  let floorDrawerTimer;
  let floorDrawerOpen = false;
  let minimizedDrawnCardId;
  let pendingRotatedRoom;
  let draggedInventoryCard;
  let cardDropTarget;

  const svg = () => viewport.querySelector("#board");
  const world = () => viewport.querySelector("#world");
  const roomDetails = () => viewport.querySelector("#room-details");
  const playerDetails = () => viewport.querySelector("#player-details");
  const monsterDetails = () => viewport.querySelector("#monster-details");
  let hideDetailsTimer;
  let pendingRoomDetailsTimer;
  let pendingRoomDetailsTarget;
  let shownRoomDetailsTarget;

  function submitHiddenForm(id, values) {
    const form = viewport.querySelector(`#${id}`);
    for (const [name, value] of Object.entries(values)) {
      form.elements.namedItem(name).value = value;
    }
    form.requestSubmit();
  }

  function submitSelectedForm(event) {
    const select = event.target.closest("select[data-submit-on-change]");
    if (!select?.value) return;
    select.form?.requestSubmit();
  }

  function applyView() {
    const board = svg();
    world()?.setAttribute(
      "transform",
      `translate(${view.x} ${view.y}) scale(${view.scale})`
    );
    board?.style.setProperty("--grid-x", `${view.x}px`);
    board?.style.setProperty("--grid-y", `${view.y}px`);
    board?.style.setProperty("--grid-size", `${GRID_SIZE * view.scale}px`);
  }

  function floorButton(key = selectedFloor) {
    return viewport.querySelector(`[data-floor-select="${key}"]`);
  }

  function setHoveredFloor(key) {
    if (hoveredFloor === key) return;
    if (hoveredFloor) {
      floorButton(hoveredFloor)?.classList.remove("drag-hover");
    }
    hoveredFloor = key;
    if (hoveredFloor) {
      floorButton(hoveredFloor)?.classList.add("drag-hover");
    }
  }

  function syncFloorDrawer() {
    const navigation = viewport.querySelector("#floor-navigation");
    const toggle = viewport.querySelector("#floor-drawer-toggle");
    navigation?.classList.toggle("open", floorDrawerOpen);
    toggle?.setAttribute("aria-expanded", String(floorDrawerOpen));
    toggle?.setAttribute(
      "aria-label",
      `${floorDrawerOpen ? "Hide" : "Show"} floor switcher`
    );
  }

  function setFloorDrawer(open) {
    floorDrawerOpen = open;
    syncFloorDrawer();
  }

  function openFloorDrawer() {
    clearTimeout(floorDrawerTimer);
    setFloorDrawer(true);
  }

  function scheduleFloorDrawerClose(delay = 650) {
    clearTimeout(floorDrawerTimer);
    floorDrawerTimer = setTimeout(() => {
      const navigation = viewport.querySelector("#floor-navigation");
      if (gesture || navigation?.matches(":hover")) {
        scheduleFloorDrawerClose(delay);
        return;
      }
      setFloorDrawer(false);
    }, delay);
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
    const rightEdge = Math.max(gap, width - gap);
    const bottomEdge = Math.max(gap, height - gap);
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
          left: Math.max(gap, bounds.left - viewportBounds.left - gap),
          right: Math.min(
            rightEdge,
            bounds.right - viewportBounds.left + gap
          ),
          top: Math.max(gap, bounds.top - viewportBounds.top - gap),
          bottom: Math.min(
            bottomEdge,
            bounds.bottom - viewportBounds.top + gap
          ),
        };
      });
    const xs = new Set([gap, rightEdge]);
    const ys = new Set([gap, bottomEdge]);
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
    const minX = Number(state.dataset.minX) - 1;
    const maxX = Number(state.dataset.maxX) + 1;
    const minY = Number(state.dataset.minY) - 1;
    const maxY = Number(state.dataset.maxY) + 1;
    const widthInCells = maxX - minX + 1;
    const heightInCells = maxY - minY + 1;
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
      minX * CELL_SIZE * view.scale;
    view.y =
      available.top +
      (available.height - boardHeight * view.scale) / 2 -
      minY * CELL_SIZE * view.scale;
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

  function centerRoom(key, gridX, gridY) {
    if (!key || !Number.isFinite(gridX) || !Number.isFinite(gridY)) return;
    if (key !== selectedFloor) {
      if (selectedFloor) floorViews[selectedFloor] = { ...view };
      selectedFloor = key;
      syncFloorControls();
      if (floorViews[key]) {
        view = { ...floorViews[key] };
      } else {
        fitBoard();
      }
    }
    const available = visibleBoardArea(CELL_SIZE, CELL_SIZE);
    view.x =
      available.left +
      available.width / 2 -
      (gridX + 0.5) * CELL_SIZE * view.scale;
    view.y =
      available.top +
      available.height / 2 -
      (gridY + 0.5) * CELL_SIZE * view.scale;
    floorViews[key] = { ...view };
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

  function resetRoomCardViews(details = roomDetails()) {
    if (!details) return;
    details.scrollTop = 0;
    details.querySelectorAll(".room-card").forEach((card) => {
      card.open = false;
    });
    details.querySelectorAll(".room-card-content").forEach((content) => {
      content.scrollTop = 0;
    });
  }

  function hideBoardDetails() {
    clearTimeout(hideDetailsTimer);
    clearTimeout(pendingRoomDetailsTimer);
    pendingRoomDetailsTarget = null;
    shownRoomDetailsTarget = null;
    for (const details of [roomDetails(), playerDetails(), monsterDetails()]) {
      if (!details) continue;
      details.hidden = true;
      details.setAttribute("aria-hidden", "true");
    }
    const roomActions = roomDetails()?.querySelector(".room-actions-menu");
    if (roomActions) roomActions.open = false;
    resetRoomCardViews();
  }

  function scheduleHideBoardDetails() {
    clearTimeout(hideDetailsTimer);
    hideDetailsTimer = setTimeout(hideBoardDetails, BOARD_DETAILS_DELAY);
  }

  function cancelPendingRoomDetails() {
    clearTimeout(pendingRoomDetailsTimer);
    pendingRoomDetailsTarget = null;
  }

  function positionBoardDetails(details, target) {
    const viewportBox = viewport.getBoundingClientRect();
    const targetBox = target.getBoundingClientRect();
    const gap = 16;
    let availableBottom = viewport.clientHeight - gap;
    if (details === roomDetails()) {
      const characterPanel = viewport.querySelector("#character-panel");
      if (characterPanel?.getClientRects().length) {
        availableBottom = Math.min(
          availableBottom,
          characterPanel.getBoundingClientRect().top - viewportBox.top - gap
        );
      }
      details.style.maxHeight = `${Math.max(180, availableBottom - gap)}px`;
    }
    const right = targetBox.right - viewportBox.left + gap;
    const left =
      targetBox.left - viewportBox.left - details.offsetWidth - gap;
    const desiredX =
      right + details.offsetWidth <= viewport.clientWidth - gap ? right : left;
    const desiredY = targetBox.top - viewportBox.top;
    details.style.left = `${Math.max(gap, desiredX)}px`;
    details.style.top = `${Math.max(
      gap,
      Math.min(desiredY, availableBottom - details.offsetHeight)
    )}px`;
  }

  function renderRoomDescription(container, description) {
    const lines = description
      ? description.split(/\r?\n/).filter((line) => line.trim())
      : ["No additional room rules."];
    const content = document.createDocumentFragment();
    let tableBody;

    for (const line of lines) {
      if (line === "<rollTable>") {
        const table = document.createElement("table");
        table.className = "roll-table";
        tableBody = table.createTBody();
        content.append(table);
        continue;
      }

      if (!tableBody) {
        const paragraph = document.createElement("p");
        paragraph.textContent = line;
        content.append(paragraph);
        continue;
      }

      const [, targetText, outcome] = line.match(/^(\S+)\s{2,}(.*)$/);
      const row = tableBody.insertRow();
      const target = document.createElement("th");
      target.scope = "row";
      target.textContent = targetText;
      row.append(target);
      row.insertCell().textContent = outcome;
    }

    container.replaceChildren(content);
  }

  function showRoomDetails(room) {
    clearTimeout(hideDetailsTimer);
    cancelPendingRoomDetails();
    shownRoomDetailsTarget = room;
    const details = roomDetails();
    if (!details) return;
    const roomActions = details.querySelector(".room-actions-menu");
    const actionsAvailable = room.matches(".room-cell");
    const detailsKey = actionsAvailable ? `board-${room.dataset.id}` : "room-stack";
    if (details.dataset.roomKey !== detailsKey) {
      if (roomActions) roomActions.open = false;
      resetRoomCardViews(details);
    }
    details.dataset.roomKey = detailsKey;
    if (roomActions) roomActions.hidden = !actionsAvailable;
    const playerCard = playerDetails();
    if (playerCard) playerCard.hidden = true;
    const monsterCard = monsterDetails();
    if (monsterCard) monsterCard.hidden = true;
    details.querySelector(".room-details-name").textContent =
      room.dataset.roomName;
    renderRoomDescription(
      details.querySelector(".room-details-description"),
      room.dataset.description
    );
    details.querySelectorAll("[data-room-cards-for]").forEach((cardList) => {
      cardList.hidden =
        !actionsAvailable || cardList.dataset.roomCardsFor !== room.dataset.id;
    });
    if (actionsAvailable) {
      details.querySelectorAll(".room-details-id").forEach((input) => {
        input.value = room.dataset.id;
      });
    }
    details.hidden = false;
    details.setAttribute("aria-hidden", "false");
    positionBoardDetails(details, room);
  }

  function scheduleRoomDetails(room) {
    clearTimeout(hideDetailsTimer);
    if (pendingRoomDetailsTarget === room) return;
    cancelPendingRoomDetails();
    pendingRoomDetailsTarget = room;
    pendingRoomDetailsTimer = setTimeout(
      () => showRoomDetails(room),
      BOARD_DETAILS_DELAY
    );
  }

  function repositionRoomDetailsAfterToggle(event) {
    if (!event.target.matches(".room-card")) return;
    const details = roomDetails();
    const target = shownRoomDetailsTarget;
    if (!details || details.hidden || !target) return;
    requestAnimationFrame(() => {
      if (
        details === roomDetails() &&
        !details.hidden &&
        target === shownRoomDetailsTarget
      ) {
        positionBoardDetails(details, target);
      }
    });
  }

  function rememberRotatedRoom(event) {
    const form = event.target.closest('[data-room-action="rotate"]');
    if (!form) return;
    pendingRotatedRoom = {
      boardState: viewport.querySelector("#board-state"),
      id: form.elements.namedItem("room-id").value,
    };
  }

  function restoreRotatedRoomDetails() {
    if (!pendingRotatedRoom) return;
    const boardState = viewport.querySelector("#board-state");
    if (!boardState || boardState === pendingRotatedRoom.boardState) return;

    const room = Array.from(boardState.querySelectorAll(".room-cell")).find(
      (candidate) => candidate.dataset.id === pendingRotatedRoom.id
    );
    pendingRotatedRoom = undefined;
    if (!room) return;

    showRoomDetails(room);
    const roomActions = roomDetails()?.querySelector(".room-actions-menu");
    if (roomActions) roomActions.open = true;
  }

  function showPlayerDetails(player) {
    clearTimeout(hideDetailsTimer);
    cancelPendingRoomDetails();
    shownRoomDetailsTarget = null;
    const details = playerDetails();
    if (!details) return;
    const roomCard = roomDetails();
    if (roomCard) roomCard.hidden = true;
    const monsterCard = monsterDetails();
    if (monsterCard) monsterCard.hidden = true;
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

  function showMonsterDetails(monster) {
    clearTimeout(hideDetailsTimer);
    cancelPendingRoomDetails();
    shownRoomDetailsTarget = null;
    const details = monsterDetails();
    if (!details) return;
    const roomCard = roomDetails();
    if (roomCard) roomCard.hidden = true;
    const playerCard = playerDetails();
    if (playerCard) playerCard.hidden = true;
    const name = monster.dataset.monsterName;
    details.querySelector(".monster-details-name").textContent =
      name || `Monster ${monster.dataset.monsterNumber}`;
    details.querySelector(".monster-details-number").textContent =
      `Monster token ${monster.dataset.monsterNumber}`;
    details.querySelector(".monster-details-id").value = monster.dataset.id;
    details.querySelector(".monster-details-name-input").value = name;
    details.hidden = false;
    details.setAttribute("aria-hidden", "false");
    positionBoardDetails(details, monster);
  }

  function updateBoardDetails(event) {
    if (gesture) {
      hideBoardDetails();
      return;
    }

    if (event.target.closest("#room-details, #monster-details")) {
      clearTimeout(hideDetailsTimer);
      cancelPendingRoomDetails();
      return;
    }

    const room = event.target.closest(
      ".room-cell, .room-picker-preview.flipped"
    );
    if (room) {
      if (
        shownRoomDetailsTarget &&
        shownRoomDetailsTarget !== room &&
        !roomDetails()?.hidden
      ) {
        scheduleRoomDetails(room);
      } else {
        showRoomDetails(room);
      }
      return;
    }

    const player = event.target.closest(".token.player");
    if (player) {
      showPlayerDetails(player);
      return;
    }

    const monster = event.target.closest(".token.monster");
    if (monster) {
      showMonsterDetails(monster);
      return;
    }

    cancelPendingRoomDetails();
    scheduleHideBoardDetails();
  }

  function beginGesture(event) {
    if (event.button !== 0) return;
    if (
      event.target.closest(
        "#game-ui, #floor-navigation, #room-details, #monster-details, .open-spot"
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

  function openFloorDrawerNearEdge(event) {
    if (floorDrawerOpen || gesture?.type !== "piece") return;
    const bounds = viewport.getBoundingClientRect();
    if (
      event.clientX >= bounds.left &&
      event.clientX <= bounds.left + FLOOR_DRAWER_EDGE_ZONE
    ) {
      openFloorDrawer();
    }
  }

  function updateFloorHover(event) {
    if (gesture?.type !== "piece") return;
    const button = document
      .elementFromPoint(event.clientX, event.clientY)
      ?.closest("[data-floor-select]");
    const key = button?.dataset.floorSelect;
    if (!key || key === selectedFloor) {
      clearTimeout(floorHoverTimer);
      setHoveredFloor(null);
      return;
    }
    if (key === hoveredFloor) return;
    clearTimeout(floorHoverTimer);
    setHoveredFloor(key);
    floorHoverTimer = setTimeout(() => {
      selectFloor(key, true);
      setHoveredFloor(null);
    }, 500);
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
    openFloorDrawerNearEdge(event);
    updateFloorHover(event);
  }

  function finishGesture(event) {
    if (!gesture || gesture.pointerId !== event.pointerId) return;
    const completed = gesture;
    gesture = null;
    clearTimeout(floorHoverTimer);
    setHoveredFloor(null);
    svg().classList.remove("panning");
    if (completed.type !== "piece") return;

    completed.element.classList.remove("dragging");
    completed.companion?.classList.remove("dragging");
    completed.preview?.remove();
    scheduleFloorDrawerClose();
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

  function clearGameSearch(panel) {
    const input = panel.querySelector("#game-search-input");
    if (input) {
      window.htmx?.trigger(input, "htmx:abort");
      input.value = "";
    }
    panel.querySelector("#search-results")?.replaceChildren();
  }

  function openGameSearch(event) {
    const button = event.target.closest("[data-game-search-toggle]");
    if (!button) return;
    const panel = button.closest("#search-panel");
    if (!panel) return;
    clearGameSearch(panel);
    panel.querySelector("#game-search-input")?.focus();
  }

  function syncGameSearchState(event) {
    const panel = event.target.closest("#search-panel");
    if (!panel) return;
    if (event.type === "focusout" && panel.contains(event.relatedTarget)) return;
    if (event.type === "focusout") clearGameSearch(panel);
    panel
      .querySelector("[data-game-search-toggle]")
      ?.setAttribute("aria-expanded", String(event.type === "focusin"));
  }

  function jumpToLocation(event) {
    const button = event.target.closest("[data-jump-location]");
    if (!button) return;
    const floor = button.dataset.floor;
    const gridX = Number(button.dataset.gridX);
    const gridY = Number(button.dataset.gridY);
    const panel = button.closest("#search-panel");
    if (panel) {
      clearGameSearch(panel);
      panel
        .querySelector("[data-game-search-toggle]")
        ?.setAttribute("aria-expanded", "false");
    }
    centerRoom(floor, gridX, gridY);
  }

  function syncDrawnCard() {
    const card = viewport.querySelector("#drawn-card-overlay");
    if (!card) {
      minimizedDrawnCardId = undefined;
      return;
    }
    card.classList.toggle(
      "minimized",
      card.dataset.drawnCardId === minimizedDrawnCardId
    );
  }

  function changeDrawnCardView(event) {
    const button = event.target.closest("[data-drawn-card-view]");
    if (!button) return;
    const card = button.closest("#drawn-card-overlay");
    minimizedDrawnCardId =
      button.dataset.drawnCardView === "minimized"
        ? card.dataset.drawnCardId
        : undefined;
    syncDrawnCard();
    const nextControl = card.querySelector(
      minimizedDrawnCardId
        ? ".drawn-card-expand"
        : ".drawn-card-minimize"
    );
    nextControl?.focus();
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

  function clearCardDropTarget() {
    cardDropTarget?.classList.remove("card-drop-target");
    cardDropTarget = undefined;
  }

  function roomAtDragEvent(event) {
    const directRoom = event.target.closest(".room-cell");
    if (directRoom) return directRoom;
    if (!world()?.getScreenCTM()) return;
    const point = clientToWorld(event.clientX, event.clientY);
    const gridX = Math.floor(point.x / CELL_SIZE);
    const gridY = Math.floor(point.y / CELL_SIZE);
    return viewport.querySelector(
      `.floor-canvas[data-floor="${selectedFloor}"] ` +
        `.room-cell[data-grid-x="${gridX}"][data-grid-y="${gridY}"]`
    );
  }

  function beginCardDrag(event) {
    const card = event.target.closest("[data-inventory-card]");
    if (!card) return;
    draggedInventoryCard = {
      playerId: card.dataset.playerId,
      cardId: card.dataset.cardId,
      element: card,
    };
    card.classList.add("dragging-card");
    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData("text/plain", card.dataset.cardId);
    hideBoardDetails();
  }

  function updateCardDrag(event) {
    if (!draggedInventoryCard) return;
    const room = roomAtDragEvent(event);
    clearCardDropTarget();
    if (!room) return;
    event.preventDefault();
    event.dataTransfer.dropEffect = "move";
    cardDropTarget = room;
    room.classList.add("card-drop-target");
  }

  function finishCardDrag(event) {
    if (!draggedInventoryCard) return;
    const room = roomAtDragEvent(event);
    if (event.type === "drop" && room) {
      event.preventDefault();
      submitHiddenForm("place-card-command", {
        "player-id": draggedInventoryCard.playerId,
        "card-id": draggedInventoryCard.cardId,
        "room-id": room.dataset.id,
      });
    }
    draggedInventoryCard.element?.classList.remove("dragging-card");
    draggedInventoryCard = undefined;
    clearCardDropTarget();
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

  function toggleFloorDrawer(event) {
    if (!event.target.closest("#floor-drawer-toggle")) return;
    if (floorDrawerOpen) {
      clearTimeout(floorDrawerTimer);
      setFloorDrawer(false);
    } else {
      openFloorDrawer();
      scheduleFloorDrawerClose();
    }
  }

  function enterFloorDrawer(event) {
    if (event.target.closest("#floor-navigation")) openFloorDrawer();
  }

  function leaveFloorDrawer(event) {
    const navigation = event.target.closest("#floor-navigation");
    if (!navigation || navigation.contains(event.relatedTarget)) return;
    scheduleFloorDrawerClose();
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
  viewport.addEventListener("toggle", repositionRoomDetailsAfterToggle, true);
  viewport.addEventListener("change", submitSelectedForm);
  viewport.addEventListener("change", selectViewedPlayer);
  viewport.addEventListener("submit", rememberRotatedRoom);
  viewport.addEventListener("click", closeInventoryCard);
  viewport.addEventListener("click", openGameSearch);
  viewport.addEventListener("click", jumpToLocation);
  viewport.addEventListener("click", changeDrawnCardView);
  viewport.addEventListener("click", changeBoardView);
  viewport.addEventListener("click", changeFloor);
  viewport.addEventListener("click", toggleFloorDrawer);
  viewport.addEventListener("click", placeRoom);
  viewport.addEventListener("dragstart", beginCardDrag);
  viewport.addEventListener("dragover", updateCardDrag);
  viewport.addEventListener("drop", finishCardDrag);
  viewport.addEventListener("dragend", finishCardDrag);
  viewport.addEventListener("pointerover", enterFloorDrawer);
  viewport.addEventListener("pointerout", leaveFloorDrawer);
  viewport.addEventListener("focusin", syncGameSearchState);
  viewport.addEventListener("focusout", syncGameSearchState);
  viewport.addEventListener(
    "wheel",
    (event) => {
      if (
        event.target.closest(
          "#game-ui, #floor-navigation, #room-details, #player-details"
        )
      )
        return;
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
    syncFloorDrawer();
    syncFloorControls();
    applyView();
    syncCharacterPanel();
    syncDrawnCard();
    restoreRotatedRoomDetails();
  });
  syncCharacterPanel();
  syncDrawnCard();
  syncFloorDrawer();
  syncFloorControls();
  fitBoard();
})();
