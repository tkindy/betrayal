import { useAppSelector } from '../../../hooks';
import { Point, translate } from '../../geometry';
import { BoundingBox } from '../../layout';

// (0, 0) is top-left square on first render
export interface GridLoc {
  gridX: number;
  gridY: number;
}

export const equal: (l1: GridLoc, l2: GridLoc) => boolean = (l1, l2) => {
  return l1.gridX === l2.gridX && l1.gridY === l2.gridY;
};

export const toString: (loc: GridLoc) => string = ({ gridX, gridY }) => {
  return `(${gridX}, ${gridY})`;
};

export const useGridSize = () => {
  return useAppSelector((state) => state.zoom.gridSize);
};

export const useGridBox: (loc: GridLoc) => BoundingBox = ({ gridX, gridY }) => {
  const gridSize = useGridSize();
  return {
    topLeft: { x: gridX * gridSize, y: gridY * gridSize },
    dimensions: { width: gridSize, height: gridSize },
  };
};

export const pointToGridLoc: (p: Point, gridSize: number) => GridLoc = (
  { x, y },
  gridSize
) => {
  return {
    gridX: Math.floor(x / gridSize),
    gridY: Math.floor(y / gridSize),
  };
};

export const windowToGridLoc: (
  windowPoint: Point,
  gridSize: number,
  boardTopLeft: Point
) => GridLoc = (windowPoint, gridSize, { x: boardX, y: boardY }) => {
  return pointToGridLoc(translate(windowPoint, boardX, boardY), gridSize);
};
