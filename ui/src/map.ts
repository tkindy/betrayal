import { Point } from './components/geometry';

type CartColumn<T> = Map<number, T>;
export type CartMap<T> = Map<number, CartColumn<T>>;

const set: <T>(map: CartMap<T>, p: Point, t: T) => CartMap<T> = <T>(
  map: CartMap<T>,
  { x, y }: Point,
  t: T
) => {
  const column: CartColumn<T> = map.get(x) || new Map();
  return map.set(x, column.set(y, t));
};

export const buildCartMap: <T>(
  ts: T[],
  getPoint: (t: T) => Point
) => CartMap<T> = <T>(ts: T[], getPoint: (t: T) => Point) => {
  return ts.reduce((map, t) => {
    return set(map, getPoint(t), t);
  }, new Map() as CartMap<T>);
};

export const buildMultiCartMap: <T>(
  ts: T[],
  getPoint: (t: T) => Point
) => CartMap<T[]> = <T>(ts: T[], getPoint: (t: T) => Point) => {
  return ts.reduce((map, t) => {
    const { x, y } = getPoint(t);
    const column: CartColumn<T[]> = map.get(x) || new Map();
    return map.set(x, column.set(y, (column.get(y) || []).concat(t)));
  }, new Map() as CartMap<T[]>);
};

export const getXY: <T>(
  map: CartMap<T>,
  x: number,
  y: number
) => T | undefined = (map, x, y) => {
  return map.get(x)?.get(y);
};

export const getPoint: <T>(map: CartMap<T>, p: Point) => T | undefined = (
  map,
  { x, y }
) => {
  return getXY(map, x, y);
};

export const mapCartMap: <T, R>(
  map: CartMap<T>,
  mapper: (p: Point, t: T) => R
) => R[] = <T, R>(map: CartMap<T>, mapper: (p: Point, t: T) => R) => {
  return Array.from(map.entries()).flatMap(([x, column]) =>
    Array.from(column.entries()).map(([y, t]) => mapper({ x, y }, t))
  );
};
