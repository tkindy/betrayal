(() => {
  const CELL_SIZE = 180;
  const viewport = document.querySelector("#board-viewport");
  if (!viewport) return;

  let view = { x: 0, y: 0, scale: 1 };
  let gesture = null;
  let initialized = false;

  const svg = () => viewport.querySelector("#board");
  const world = () => viewport.querySelector("#world");

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

  function beginGesture(event) {
    if (event.button !== 0) return;
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

  async function finishGesture(event) {
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

    const { gameId } = viewport.dataset;
    const { kind, id } = completed.element.dataset;
    const body = new URLSearchParams({
      "grid-x": gridX,
      "grid-y": gridY,
    });
    try {
      const response = await fetch(`/games/${gameId}/move/${kind}/${id}`, {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body,
      });
      const html = await response.text();
      viewport.querySelector("#board-state").outerHTML = html;
      applyView();
    } catch (_) {
      completed.element.setAttribute(
        "transform",
        `translate(${completed.origin.x} ${completed.origin.y})`
      );
      if (completed.companion) {
        completed.companion.setAttribute(
          "transform",
          `translate(${completed.companionOrigin.x} ${completed.companionOrigin.y})`
        );
      }
    }
  }

  viewport.addEventListener("pointerdown", beginGesture);
  viewport.addEventListener("pointermove", updateGesture);
  viewport.addEventListener("pointerup", finishGesture);
  viewport.addEventListener("pointercancel", finishGesture);
  viewport.addEventListener(
    "wheel",
    (event) => {
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
  fitBoard();
})();
