import { partition } from './utils';

it('partitions arrays', () => {
  expect(partition([], 5)).toEqual([]);
  expect(partition([1, 2, 3], 5)).toEqual([[1, 2, 3]]);
  expect(partition([1, 2, 3, 4, 5], 2)).toEqual([[1, 2], [3, 4], [5]]);
});
