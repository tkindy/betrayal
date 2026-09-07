import { createAction } from '@reduxjs/toolkit';
import { AppThunk } from '../store';
import * as lobbyActions from './lobby';
import { GameUpdatePayload } from './models';

export const joinGame = createAction<{ gameId: string }>('game/join');

interface PlayersMessage {
  type: 'players';
  players: string[];
}

interface JoinGameMessage {
  type: 'join';
}

type LobbyServerMessage = PlayersMessage | JoinGameMessage;

export const receiveLobbyMessage =
  (message: LobbyServerMessage): AppThunk =>
  (dispatch) => {
    switch (message.type) {
      case 'players':
        dispatch(lobbyActions.setPlayers(message.players));
        break;
      case 'join':
        dispatch(lobbyActions.switchToGame());
        break;
    }
  };

export const receiveGameMessage = createAction<GameUpdatePayload>(
  'game/receiveGameMessage'
);
