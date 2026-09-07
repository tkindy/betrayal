import { windowToGridLoc } from './grid';

it('computes grid loc from window point', () => {
  expect(
    windowToGridLoc(
      {
        x: 150,
        y: 30,
      },
      100,
      { x: 240, y: 100 }
    )
  ).toEqual({ gridX: 3, gridY: 1 });
});
