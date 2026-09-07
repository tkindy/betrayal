import { GridLoc, toString } from './components/game/board/grid';
import { Direction } from './components/game/room/Room';
import { Room as RoomModel } from './features/models';
import { buildCartMap, CartMap, getXY } from './map';
import { index } from './utils';

type BoardMap = CartMap<RoomModel>;

export const get: (map: BoardMap, loc: GridLoc) => RoomModel | undefined = (
  map,
  { gridX, gridY }
) => {
  return getXY(map, gridX, gridY);
};

export const buildBoardMap: (rooms: RoomModel[]) => BoardMap = (rooms) => {
  return buildCartMap(rooms, ({ loc: { gridX: x, gridY: y } }) => {
    return { x, y };
  });
};

const getDelta: (dir: Direction) => [number, number] = (dir) => {
  switch (dir) {
    case Direction.NORTH:
      return [0, -1];
    case Direction.SOUTH:
      return [0, 1];
    case Direction.EAST:
      return [1, 0];
    case Direction.WEST:
      return [-1, 0];
  }
};

interface Neighbor {
  loc: GridLoc;
  from: Direction[];
}

const getNeighbors: (room: RoomModel) => Neighbor[] = (room) => {
  const { gridX: x, gridY: y } = room.loc;

  return room.doorDirections.map((dir) => {
    const [dx, dy] = getDelta(dir);

    return {
      loc: {
        gridX: x + dx,
        gridY: y + dy,
      },
      from: [dir],
    };
  });
};

const isOpen: (loc: GridLoc, map: BoardMap) => boolean = (loc, map) => {
  const { gridX: x, gridY: y } = loc;
  return !getXY(map, x, y);
};

export const findOpenNeighbors: (map: BoardMap) => Neighbor[] = (map) => {
  const openNeighbors = Array.from(map.values())
    .flatMap((column) => Array.from(column.values()))
    .flatMap(getNeighbors)
    .filter(({ loc }) => isOpen(loc, map));

  const locIndex = index(openNeighbors, (neighbor) => toString(neighbor.loc));

  return Array.from(locIndex.values()).map((neighbors) =>
    neighbors.reduce(({ loc, from }, cur) => {
      return { loc, from: from.concat(cur.from) };
    })
  );
};
