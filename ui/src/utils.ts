export const partition: <T>(ts: T[], size: number) => T[][] = <T>(
  ts: T[],
  size: number
) => {
  return ts.reduce((acc, t, i) => {
    const [curPartition, donePartitions] =
      i % size === 0
        ? [[], acc]
        : [acc[acc.length - 1], acc.slice(0, acc.length - 1)];
    return donePartitions.concat([curPartition.concat([t])]);
  }, [] as T[][]);
};

export const index: <T, K extends number | string | boolean>(
  ts: T[],
  by: (t: T) => K
) => Map<K, T[]> = <T, K>(ts: T[], by: (t: T) => K) => {
  return ts.reduce((map, t) => {
    const key = by(t);
    const group = map.get(key) || [];

    return new Map(map).set(key, group.concat(t));
  }, new Map());
};

export const choices: <T>(array: T[], n: number) => T[] = (array, n) => {
  return Array.from(Array(n).keys())
    .map((_) => Math.floor(Math.random() * array.length))
    .map((index) => array[index]);
};

export const sortBy: <T>(
  array: T[],
  select: (t: T) => string | number
) => T[] = (array, select) => {
  const mapped = array.map((t, i) => {
    return { index: i, value: select(t) };
  });

  mapped.sort((a, b) => {
    if (a.value > b.value) {
      return 1;
    }
    if (a.value < b.value) {
      return -1;
    }
    return 0;
  });

  return mapped.map(({ index }) => array[index]);
};
