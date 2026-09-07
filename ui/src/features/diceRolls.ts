import { createSlice } from '@reduxjs/toolkit';
import { createAppAsyncThunk } from './utils';
import * as api from '../api/api';
import { getGameId } from './selectors';
import { DiceRoll, DiceRollType } from './models';
import { receiveGameMessage } from './actions';

interface RollDicePayload {
  numDice: number;
  type: DiceRollType;
}

export const rollDice = createAppAsyncThunk(
  'rollDice',
  async (payload: RollDicePayload, { getState }) => {
    return api.rollDice(getGameId(getState()), payload.numDice, payload.type);
  }
);

interface DiceRollsState {
  roll?: DiceRoll;
  couldTriggerHaunt: boolean;
}

const initialState: DiceRollsState = { couldTriggerHaunt: false };

const isNewRoll = (roll: DiceRoll, lastRoll?: DiceRoll) =>
  roll.id !== lastRoll?.id;

const diceRollsSlice = createSlice({
  name: 'diceRolls',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder.addCase(receiveGameMessage, (state, { payload: { update } }) => {
      const roll = update.latestRoll;
      const lastRoll = state.roll;
      state.roll = roll || undefined;

      if (roll && isNewRoll(roll, lastRoll)) {
        state.couldTriggerHaunt = roll.type === 'HAUNT';
      }
    });
  },
});

export default diceRollsSlice.reducer;
