export interface Point {
  x: number;
  y: number;
}

export const add: (...points: Point[]) => Point = (...points) => {
  return points.reduce(
    ({ x: accX, y: accY }, { x, y }) => {
      return { x: accX + x, y: accY + y };
    },
    { x: 0, y: 0 }
  );
};

export const multiply: (p1: Point, c: number) => Point = ({ x, y }, c) => {
  return { x: x * c, y: y * c };
};

export const subtract: (p1: Point, p2: Point) => Point = (p1, p2) => {
  return add(p1, multiply(p2, -1));
};

export const translate: (p: Point, dx: number, dy: number) => Point = (
  { x, y },
  dx,
  dy
) => {
  return {
    x: x + dx,
    y: y + dy,
  };
};

export const rotate: (p: Point, center: Point, rotation: number) => Point = (
  p,
  center,
  rotation
) => {
  const { x, y } = subtract(center, p);

  const rotationRad = rotation * (Math.PI / 180);
  const sin = -Math.sin(rotationRad);
  const cos = Math.cos(rotationRad);

  return add(center, {
    x: -x * cos + y * sin,
    y: -x * sin - y * cos,
  });
};

export const pointsToArray: (points: Point[]) => number[] = (points) => {
  return points.map(({ x, y }) => [x, y]).flat();
};
