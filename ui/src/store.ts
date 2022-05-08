import { Action, configureStore, Middleware } from '@reduxjs/toolkit';
import { combineReducers } from '@reduxjs/toolkit';
import zoomReducer from './features/zoom';
import roomStackReducer from './features/roomStack';
import cardStacksReducer from './features/cardStacks';
import boardReducer from './features/board';
import playersReducer from './features/players';
import gameReducer from './features/game';
import diceRollsReducer, {
  RollDicePayload,
  setRoll,
} from './features/diceRolls';
import monstersReducer from './features/monsters';
import reduxWebsocket from '@giantmachines/redux-websocket';
import { GameUpdate } from './features/models';
import { webSocketThunk } from './websocket-thunk';
import { choices, delayAtLeast } from './utils';
import { createAppAsyncThunk } from './features/utils';
import { getGameId } from './features/selectors';

const rootReducer = combineReducers({
  game: gameReducer,
  zoom: zoomReducer,
  roomStack: roomStackReducer,
  cardStacks: cardStacksReducer,
  board: boardReducer,
  players: playersReducer,
  diceRolls: diceRollsReducer,
  monsters: monstersReducer,
});

export const store = configureStore({
  reducer: rootReducer,
  middleware: (getDefaultMiddleware) =>
    getDefaultMiddleware().concat([
      reduxWebsocket({
        deserializer: (message) => JSON.parse(message),
        dateSerializer: (date) => date.getUTCMilliseconds(),
      }),
      webSocketThunk,
    ]),
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;

export interface GameUpdatePayload {
  message: GameUpdate;
}
