import { Point, translate } from './geometry';

export interface Dimensions {
  width: number;
  height: number;
}

export interface BoundingBox {
  topLeft: Point;
  dimensions: Dimensions;
}

export const getCenter: (box: BoundingBox) => Point = ({
  topLeft,
  dimensions: { width, height },
}) => {
  return translate(topLeft, width / 2, height / 2);
};

export const getDoorDimensions: (roomDimensions: Dimensions) => Dimensions = ({
  width: roomWidth,
  height: roomHeight,
}) => {
  return {
    width: roomWidth / 2.5,
    height: roomHeight / 8,
  };
};

export const getPlayersBox: (roomBox: BoundingBox) => BoundingBox = ({
  topLeft: roomTopLeft,
  dimensions: roomDimensions,
}) => {
  const { height: doorHeight } = getDoorDimensions(roomDimensions);
  const { width: roomWidth, height: roomHeight } = roomDimensions;

  return {
    topLeft: translate(roomTopLeft, doorHeight, roomHeight / 2),
    dimensions: {
      width: roomWidth - 2 * doorHeight,
      height: roomHeight / 2 - doorHeight,
    },
  };
};

export const getRoomDetailsBox: (roomBox: BoundingBox) => BoundingBox = ({
  topLeft: roomTopLeft,
  dimensions: roomDimensions,
}) => {
  const { height: doorHeight } = getDoorDimensions(roomDimensions);
  const { width: roomWidth, height: roomHeight } = roomDimensions;

  return {
    topLeft: translate(roomTopLeft, doorHeight, doorHeight),
    dimensions: {
      width: roomWidth - 2 * doorHeight,
      height: roomHeight / 2 - doorHeight,
    },
  };
};
