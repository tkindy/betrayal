import { createSlice, PayloadAction } from '@reduxjs/toolkit';
import { AppDispatch } from '../store';

interface LobbyState {
  lobbyId?: string;
  name?: string;
  isHost?: boolean;
  players?: string[];
  gameStarted: boolean;
}

const initialState: LobbyState = { gameStarted: false };

const lobbySlice = createSlice({
  name: 'lobby',
  initialState,
  reducers: {
    setName(
      state,
      {
        payload: { name, isHost },
      }: PayloadAction<{ name: string; isHost: boolean }>
    ) {
      state.name = name;
      state.isHost = isHost;
    },
    setLobbyId(state, { payload: lobbyId }: PayloadAction<string>) {
      state.lobbyId = lobbyId;
    },
    setPlayers(state, { payload: players }: PayloadAction<string[]>) {
      state.players = players;
    },
    switchToGame(state) {
      state.gameStarted = true;
    },
  },
});

export const setName =
  (lobbyId: string, name: string, isHost: boolean) =>
  (dispatch: AppDispatch) => {
    if (process.env.NODE_ENV === 'production') {
      localStorage.setItem(lobbyId, name);
    }
    dispatch(lobbySlice.actions.setName({ name, isHost }));
  };

export const { setPlayers, switchToGame } = lobbySlice.actions;
export default lobbySlice.reducer;
