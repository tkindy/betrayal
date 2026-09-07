import { AnyAction, configureStore, ThunkAction } from '@reduxjs/toolkit';
import { combineReducers } from '@reduxjs/toolkit';
import lobbyReducer from './features/lobby';
import zoomReducer from './features/zoom';
import roomStackReducer from './features/roomStack';
import cardStacksReducer from './features/cardStacks';
import boardReducer from './features/board';
import playersReducer from './features/players';
import gameReducer from './features/game';
import diceRollsReducer from './features/diceRolls';
import monstersReducer from './features/monsters';

const rootReducer = combineReducers({
  lobby: lobbyReducer,
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
});

export type RootState = ReturnType<typeof store.getState>;
export type AppDispatch = typeof store.dispatch;
export type AppThunk<ReturnType = void> = ThunkAction<
  ReturnType,
  RootState,
  unknown,
  AnyAction
>;
