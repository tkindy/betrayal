import { createSlice } from '@reduxjs/toolkit';
import * as api from '../api/api';
import { receiveGameMessage } from './actions';
import { Card, Player } from './models';
import { getGameId } from './selectors';
import { createAppAsyncThunk } from './utils';

export const drawEvent = createAppAsyncThunk(
  'cardStacks/events/get',
  async (_, { getState }) => {
    return api.drawEvent(getGameId(getState()));
  }
);

export const drawItem = createAppAsyncThunk(
  'cardStacks/items/get',
  async (_, { getState }) => {
    return api.drawItem(getGameId(getState()));
  }
);

export const drawOmen = createAppAsyncThunk(
  'cardStacks/omens/get',
  async (_, { getState }) => {
    return api.drawOmen(getGameId(getState()));
  }
);

export const discardDrawnCard = createAppAsyncThunk(
  'cardStacks/drawn/discard',
  async (_, { getState }) => {
    return api.discardDrawnCard(getGameId(getState()));
  }
);

export const giveDrawnCardToPlayer = createAppAsyncThunk<
  Player,
  { playerId: number }
>('cardStacks/drawn/giveToPlayer', async ({ playerId }, { getState }) => {
  return api.giveDrawnCardToPlayer(getGameId(getState()), playerId);
});

interface CardStacksState {
  drawnCard: Card | null;
}

const initialState: CardStacksState = {
  drawnCard: null,
};

const cardStacksSlice = createSlice({
  name: 'cardStacks',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(drawEvent.fulfilled, (state, { payload: card }) => {
        state.drawnCard = card;
      })
      .addCase(drawItem.fulfilled, (state, { payload: card }) => {
        state.drawnCard = card;
      })
      .addCase(drawOmen.fulfilled, (state, { payload: card }) => {
        state.drawnCard = card;
      })
      .addCase(discardDrawnCard.fulfilled, (state) => {
        state.drawnCard = null;
      })
      .addCase(giveDrawnCardToPlayer.fulfilled, (state) => {
        state.drawnCard = null;
      })
      .addCase(receiveGameMessage, (state, { payload: { update } }) => {
        state.drawnCard = update.drawnCard;
      });
  },
});

export default cardStacksSlice.reducer;
