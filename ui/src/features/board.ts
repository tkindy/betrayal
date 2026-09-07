import {
  Action,
  createSlice,
  PayloadAction,
  ThunkAction,
} from '@reduxjs/toolkit';
import { equal, GridLoc } from '../components/game/board/grid';
import { RootState } from '../store';
import * as api from '../api/api';
import { Room } from './models';
import { Point } from '../components/geometry';
import { getGameId, getOpenNeighbors } from './selectors';
import { Direction } from '../components/game/room/Room';
import { createAppAsyncThunk } from './utils';
import { receiveGameMessage } from './actions';

export const moveRoom = createAppAsyncThunk(
  'board/moveRoom',
  async ({ id, loc }: { id: number; loc: GridLoc }, { getState }) => {
    return api.moveRoom(getGameId(getState()), id, loc);
  }
);

export const rotateRoom = createAppAsyncThunk(
  'board/rotateRoom',
  async ({ id }: { id: number }, { getState }) => {
    return api.rotateRoom(getGameId(getState()), id);
  }
);

export const returnRoomToStack = createAppAsyncThunk(
  'board/returnRoomToStack',
  async ({ id }: { id: number }, { getState }) => {
    return api.returnRoomToStack(getGameId(getState()), id);
  }
);

interface BoardState {
  topLeft: Point;
  rooms?: Room[];
}

const getMatchingDoor: (dir: Direction) => Direction = (dir) => {
  switch (dir) {
    case Direction.NORTH:
      return Direction.SOUTH;
    case Direction.SOUTH:
      return Direction.NORTH;
    case Direction.EAST:
      return Direction.WEST;
    case Direction.WEST:
      return Direction.EAST;
  }
};

export const placeRoom = createAppAsyncThunk(
  'board/placeRoomStatus',
  async (loc: GridLoc, { getState }) => {
    return api.placeRoom(getGameId(getState()), loc);
  }
);

export const openSpotClicked: (
  loc: GridLoc,
  from: Direction[]
) => ThunkAction<void, RootState, unknown, Action<string>> =
  (loc, from) => (dispatch, getState) => {
    const flippedRoom = getState().roomStack.flippedRoom;
    if (!flippedRoom) {
      return;
    }

    const hasMatchingDoor = flippedRoom.doorDirections
      .map(getMatchingDoor)
      .some((dir) => from.includes(dir));

    if (!hasMatchingDoor) {
      return;
    }

    dispatch(placeRoom(loc));
  };

export const flippedRoomDropped: (
  loc: GridLoc
) => ThunkAction<void, RootState, unknown, Action<string>> =
  (loc) => (dispatch, getState) => {
    const openNeighbors = getOpenNeighbors(getState());
    const relevantNeighbor = openNeighbors?.find((neighbor) =>
      equal(neighbor.loc, loc)
    );

    if (!relevantNeighbor) {
      return;
    }

    dispatch(openSpotClicked(relevantNeighbor.loc, relevantNeighbor.from));
  };

const initialState: BoardState = {
  topLeft: { x: 0, y: 0 },
};

const boardSlice = createSlice({
  name: 'board',
  initialState,
  reducers: {
    moveBoard(state, action: PayloadAction<Point>) {
      state.topLeft = action.payload;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(placeRoom.fulfilled, (state, { payload: { rooms } }) => {
        state.rooms = rooms;
      })
      .addCase(receiveGameMessage, (state, { payload: { update } }) => {
        state.rooms = update.rooms;
      });
  },
});

export const { moveBoard } = boardSlice.actions;
export default boardSlice.reducer;
