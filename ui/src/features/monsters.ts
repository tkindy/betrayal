import { Action, createSlice, ThunkAction } from '@reduxjs/toolkit';
import { Monster } from './models';
import { createAppAsyncThunk } from './utils';
import * as api from '../api/api';
import { getBoardMap, getGameId } from './selectors';
import { equal, GridLoc } from '../components/game/board/grid';
import { RootState } from '../store';
import { get } from '../board';
import { getMonsters as selectMonsters } from './selectors';
import { receiveGameMessage } from './actions';

export const addMonster = createAppAsyncThunk(
  'monsters/add',
  async (_, { getState }) => {
    return api.addMonster(getGameId(getState()));
  }
);

export const moveMonster = createAppAsyncThunk(
  'monsters/move',
  async ({ id, loc }: { id: number; loc: GridLoc }, { getState }) => {
    return api.moveMonster(getGameId(getState()), id, loc);
  }
);

export const monsterDropped: (
  id: number,
  loc: GridLoc
) => ThunkAction<void, RootState, unknown, Action<string>> =
  (id, loc) => (dispatch, getState) => {
    const map = getBoardMap(getState())!!;
    if (!get(map, loc)) {
      return;
    }

    const { loc: originalLoc } = selectMonsters(getState())!!.find(
      (monster) => monster.id === id
    )!!;
    if (equal(originalLoc, loc)) {
      return;
    }

    dispatch(moveMonster({ id, loc }));
  };

interface MonstersState {
  monsters?: Monster[];
}

const initialState: MonstersState = {};

const monstersSlice = createSlice({
  name: 'monsters',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(addMonster.fulfilled, (state, { payload: monster }) => {
        if (state.monsters?.map((m) => m.id).includes(monster.id)) {
          return;
        }

        state.monsters = (state.monsters || []).concat(monster);
      })
      .addCase(moveMonster.fulfilled, (state, { payload: monster }) => {
        state.monsters = state.monsters!!.map((m) =>
          m.id === monster.id ? monster : m
        );
      })
      .addCase(receiveGameMessage, (state, { payload: { update } }) => {
        state.monsters = update.monsters;
      });
  },
});

export default monstersSlice.reducer;
