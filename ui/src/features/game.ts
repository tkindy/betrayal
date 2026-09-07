import { createSlice } from '@reduxjs/toolkit';
import { joinGame } from './actions';

interface GameState {
  gameId?: string;
}

const initialState: GameState = {};

const gameSlice = createSlice({
  name: 'game',
  initialState,
  reducers: {},
  extraReducers(builder) {
    builder.addCase(joinGame, (state, { payload: { gameId } }) => {
      state.gameId = gameId;
    });
  },
});

export default gameSlice.reducer;
