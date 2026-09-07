import {
  Action,
  createSlice,
  PayloadAction,
  ThunkAction,
} from '@reduxjs/toolkit';
import { Player, TraitName } from './models';
import * as api from '../api/api';
import { equal, GridLoc } from '../components/game/board/grid';
import { RootState } from '../store';
import {
  getBoardMap,
  getGameId,
  getPlayers as selectPlayers,
  getSelectedPlayerId,
} from './selectors';
import { get } from '../board';
import { createAppAsyncThunk } from './utils';
import { giveDrawnCardToPlayer } from './cardStacks';
import { receiveGameMessage } from './actions';

interface MovePlayerPayload {
  id: number;
  loc: GridLoc;
}

export const movePlayer = createAppAsyncThunk(
  'board/movePlayerStatus',
  ({ id, loc }: MovePlayerPayload, { getState }) => {
    return api.movePlayer(getGameId(getState()), id, loc);
  }
);

export const playerDropped: (
  id: number,
  loc: GridLoc
) => ThunkAction<void, RootState, unknown, Action<string>> =
  (id, loc) => (dispatch, getState) => {
    const map = getBoardMap(getState())!!;
    if (!get(map, loc)) {
      return;
    }

    const { loc: originalLoc } = selectPlayers(getState())!!.find(
      (player) => player.id === id
    )!!;
    if (equal(originalLoc, loc)) {
      return;
    }

    dispatch(movePlayer({ id, loc }));
  };

export const setTrait = createAppAsyncThunk(
  'players/setTrait',
  async (
    {
      trait,
      index,
    }: {
      trait: TraitName;
      index: number;
    },
    { getState }
  ) => {
    return api.setTrait(
      getGameId(getState()),
      getSelectedPlayerId(getState())!!,
      trait,
      index
    );
  }
);

export const discardHeldCard = createAppAsyncThunk(
  'players/discardHeld',
  async ({ cardId }: { cardId: number }, { getState }) => {
    return api.discardHeldCard(
      getGameId(getState()),
      getSelectedPlayerId(getState())!!,
      cardId
    );
  }
);

export const giveHeldCardToPlayer = createAppAsyncThunk(
  'players/giveHeldCardToPlayer',
  async (
    { cardId, toPlayerId }: { cardId: number; toPlayerId: number },
    { getState }
  ) => {
    return api.giveHeldCardToPlayer(
      getGameId(getState()),
      getSelectedPlayerId(getState())!!,
      cardId,
      toPlayerId
    );
  }
);

const replacePlayer: (players: Player[], player: Player) => Player[] = (
  players,
  player
) => {
  return players.map((p) => (p.id === player.id ? player : p));
};

interface PlayersState {
  players?: Player[];
  selectedPlayerId?: number;
  myPlayerId?: number;
}

const initialState: PlayersState = {};

const playersSlice = createSlice({
  name: 'players',
  initialState,
  reducers: {
    switchSelectedPlayer(state, { payload: playerId }: PayloadAction<number>) {
      state.selectedPlayerId = playerId;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(movePlayer.fulfilled, (state, { payload: players }) => {
        state.players = players;
      })
      .addCase(
        giveDrawnCardToPlayer.fulfilled,
        (state, { payload: player }) => {
          state.players = replacePlayer(state.players!!, player);
        }
      )
      .addCase(discardHeldCard.fulfilled, (state, { payload: player }) => {
        state.players = replacePlayer(state.players!!, player);
      })
      .addCase(
        giveHeldCardToPlayer.fulfilled,
        (state, { payload: players }) => {
          state.players = players;
        }
      )
      .addCase(setTrait.fulfilled, (state, { payload: player }) => {
        state.players = replacePlayer(state.players!!, player);
      })
      .addCase(receiveGameMessage, (state, { payload: { name, update } }) => {
        state.players = update.players;

        if (state.myPlayerId === undefined) {
          state.myPlayerId = update.players.filter(
            (player) => player.name === name
          )[0].id;
        }
        if (state.selectedPlayerId === undefined) {
          state.selectedPlayerId = state.myPlayerId;
        }
      });
  },
});

export const { switchSelectedPlayer } = playersSlice.actions;
export default playersSlice.reducer;
