import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { addUpdateCase, createAppAsyncThunk } from './utils';
import * as api from '../api/api';
import { getGameId } from './selectors';
import { choices, delayAtLeast } from '../utils';

export interface RollDicePayload {
  numDice: number;
}

const randomRoll = (numDice: number) => {
  const universe = [0, 1, 2];
  return choices(universe, numDice);
};

export const rollDice = createAppAsyncThunk(
  'requestRollDice',
  async (payload: RollDicePayload, { getState, dispatch }) => {
    dispatch(setRoll(randomRoll(payload.numDice)));
    const interval = setInterval(() => {
      dispatch(setRoll(randomRoll(payload.numDice)));
    }, 75);

    const roll = await delayAtLeast(
      () => api.rollDice(getGameId(getState()), payload.numDice),
      600
    );

    clearInterval(interval);
    return roll;
  }
);

interface DiceRollsState {
  roll?: number[];
}

const initialState: DiceRollsState = {};

const diceRollsSlice = createSlice({
  name: 'diceRolls',
  initialState,
  reducers: {
    setRoll: (state, action: PayloadAction<number[]>) => {
      state.roll = action.payload;
    },
  },
  extraReducers: (builder) => {
    builder.addCase(rollDice.pending, (state) => {
      state.roll = undefined;
    });

    addUpdateCase(builder, (state, { payload: { message } }) => {
      state.roll = message.latestRoll || undefined;
    });
  },
});

export const { setRoll } = diceRollsSlice.actions;

export default diceRollsSlice.reducer;
