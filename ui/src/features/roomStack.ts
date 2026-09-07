import { createSlice } from '@reduxjs/toolkit';
import { placeRoom } from './board';
import { FlippedRoom, StackRoom } from './models';
import * as api from '../api/api';
import { getGameId } from './selectors';
import { createAppAsyncThunk } from './utils';
import { receiveGameMessage } from './actions';

export const skipRoom = createAppAsyncThunk(
  'roomStack/skipStatus',
  async (_, { getState }) => {
    return api.skipRoom(getGameId(getState()));
  }
);

export const flipRoomStack = createAppAsyncThunk(
  'roomStack/flipStatus',
  async (_, { getState }) => {
    return api.flipRoom(getGameId(getState()));
  }
);

export const rotateFlipped = createAppAsyncThunk(
  'roomStack/rotateStatus',
  async (_, { getState }) => {
    return api.rotateFlipped(getGameId(getState()));
  }
);

export interface RoomStackState {
  nextRoom?: StackRoom;
  flippedRoom?: FlippedRoom;
}

const initialState: RoomStackState = {};

const roomStackSlice = createSlice({
  name: 'roomStack',
  initialState,
  reducers: {},
  extraReducers: (builder) => {
    builder
      .addCase(flipRoomStack.fulfilled, (state, { payload: flippedRoom }) => {
        delete state.nextRoom;
        state.flippedRoom = flippedRoom;
      })
      .addCase(rotateFlipped.fulfilled, (state, { payload: flippedRoom }) => {
        state.flippedRoom = flippedRoom;
      })
      .addCase(placeRoom.fulfilled, (state, { payload: { nextRoom } }) => {
        delete state.flippedRoom;
        state.nextRoom = nextRoom;
      })
      .addCase(skipRoom.fulfilled, (state, { payload: nextRoom }) => {
        state.nextRoom = nextRoom;
      })
      .addCase(receiveGameMessage, (_state, { payload: { update } }) => {
        return update.roomStack;
      });
  },
});

export default roomStackSlice.reducer;
