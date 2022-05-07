import { createSlice } from '@reduxjs/toolkit';
import { addUpdateCase, createAppAsyncThunk } from './utils';
import * as api from '../api/api';
import { getGameId } from './selectors';

interface RollDicePayload {
  numDice: number;
}

export const rollDice = createAppAsyncThunk(
  'rollDice',
  async (payload: RollDicePayload, { getState }) => {
    return api.rollDice(getGameId(getState()), payload.numDice);
  }
);

interface DiceRollsState {
  roll?: number[];
  rolling: boolean;
}

const initialState: DiceRollsState = { rolling: false };

const diceRollsSlice = createSlice({
  name: 'diceRolls',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(rollDice.pending, (state) => {
        state.roll = undefined;
        state.rolling = true;
      })
      .addCase(rollDice.rejected, (state) => {
        state.rolling = false;
      })
      .addCase(rollDice.fulfilled, (state, { payload: roll }) => {
        state.roll = roll;
        state.rolling = false;
      });

    addUpdateCase(builder, (state, { payload: { message } }) => {
      state.roll = message.latestRoll || undefined;
    });
  },
});

export default diceRollsSlice.reducer;
