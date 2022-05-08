import { Middleware } from '@reduxjs/toolkit';
import { setRoll } from './features/diceRolls';
import { choices } from './utils';

const randomRoll = (numDice: number) => {
  const universe = [0, 1, 2];
  return choices(universe, numDice);
};

export const webSocketThunk: Middleware =
  ({ dispatch, getState }) =>
  (next) =>
  (action) => {
    if (action.type === 'REDUX_WEBSOCKET::MESSAGE') {
      const { message } = action.payload;
      if (message.type === 'diceRolled') {
        const { numDice } = message.payload;
        dispatch(setRoll(randomRoll(numDice)));
        const interval = setInterval(() => {
          dispatch(setRoll(randomRoll(numDice)));
        }, 75);

        setTimeout(() => clearInterval(interval), 600);
      } else {
      }
    }

    return next(action);
  };
